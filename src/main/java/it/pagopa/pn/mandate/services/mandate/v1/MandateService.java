package it.pagopa.pn.mandate.services.mandate.v1;

import java.util.Optional;
import java.util.function.Predicate;

import org.springframework.stereotype.Service; 

import it.pagopa.pn.mandate.mapper.MandateEntityMandateDtoMapper;
import it.pagopa.pn.mandate.mapper.UserEntityMandateCountsDtoMapper;
import it.pagopa.pn.mandate.middleware.db.MandateDao;
import it.pagopa.pn.mandate.middleware.db.UserDao;
import it.pagopa.pn.mandate.middleware.microservice.PnInfoPaClient;
import it.pagopa.pn.mandate.rest.mandate.v1.dto.AcceptRequestDto;
import it.pagopa.pn.mandate.rest.mandate.v1.dto.MandateCountsDto;
import it.pagopa.pn.mandate.rest.mandate.v1.dto.MandateDto;
import it.pagopa.pn.mandate.rest.mandate.v1.dto.MandateDto.StatusEnum; 
import it.pagopa.pn.mandate.rest.utils.UnsupportedFilterException;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class MandateService  {
   
    private MandateDao mandateDao;
    private UserDao userDao;
    private MandateEntityMandateDtoMapper mandateEntityMandateDtoMapper;
    private UserEntityMandateCountsDtoMapper userEntityMandateCountsDtoMapper;
    private PnInfoPaClient pnInfoPaClient;

    public MandateService(MandateDao mandateDao, UserDao userDao, MandateEntityMandateDtoMapper mandateEntityMandateDtoMapper,
        UserEntityMandateCountsDtoMapper userEntityMandateCountsDtoMapper, PnInfoPaClient pnInfoPaClient) {
        this.mandateDao = mandateDao;
        this.userDao =userDao;
        this.mandateEntityMandateDtoMapper = mandateEntityMandateDtoMapper;
        this.userEntityMandateCountsDtoMapper = userEntityMandateCountsDtoMapper;
        this.pnInfoPaClient = pnInfoPaClient;
    }
    
    
    public Mono<Object> acceptMandate(String mandateId, Mono<AcceptRequestDto> acceptRequestDto,
            String internaluserId) {
        return acceptRequestDto
        .map(m -> {
            try{
                if (log.isInfoEnabled())
                    log.info("accepting mandate " + m);
                return mandateDao.acceptMandate(internaluserId, mandateId, m.getVerificationCode());                
            }catch(Exception ex)
            {
                throw Exceptions.propagate(ex);
            }            
        });     
    }

    public Mono<MandateCountsDto> countMandatesByDelegate(String status, String internaluserId) {
       // per ora l'unico stato supportato è il pending, quindi il filtro non viene passato al count
       // Inoltre, ritorno un errore se status != pending
       if (status == null || !status.equals(StatusEnum.PENDING.getValue()))
        throw new UnsupportedFilterException();
       return userDao.countMandates(internaluserId)                
                .flatMap(entity -> {
                    return userEntityMandateCountsDtoMapper.toDto(Mono.just(entity));
                });
    }

    
    public Mono<MandateDto> createMandate(Mono<MandateDto> mandateDto, String internaluserId) {
        return mandateDto
        .map(dto -> mandateEntityMandateDtoMapper.toEntity(Mono.just(dto)))
        .map(entity -> {
            return entity;
        })
        .zipWhen(mandate -> {
            return mandate.map(m -> mandateDao.createMandate(m));            
        },
        (m, r) -> r)
        .flatMap(r -> mandateEntityMandateDtoMapper.toDto(r));        
    }

    
    public Flux<MandateDto> listMandatesByDelegate(String status, String internaluserId) {
        Optional<String> optstatus = Optional.ofNullable(status).filter(Predicate.not(String::isEmpty));
        return mandateDao.listMandatesByDelegate(internaluserId, optstatus)
                .flatMap(ent -> mandateEntityMandateDtoMapper.toDto(Mono.just(ent)))
                .flatMap(ent -> {
                    if (!ent.getVisibilityIds().isEmpty())
                        return pnInfoPaClient
                            .getOnePa(ent.getVisibilityIds().get(0).getUniqueIdentifier()) // per ora chiedo solo il primo...in futuro l'intera lista
                            .flatMap(pa -> {
                                ent.getVisibilityIds().get(0).setName(pa.getName());
                                return Mono.just(ent);
                                });   
                    else
                        return Mono.just(ent);
                })         
            ;
    }

    
    public Flux<MandateDto> listMandatesByDelegator(String internaluserId) {
        Optional<String> optstatus = Optional.ofNullable(null);
        return mandateDao.listMandatesByDelegator(internaluserId, optstatus)
            .flatMap(ent -> mandateEntityMandateDtoMapper.toDto(Mono.just(ent)))
            .flatMap(ent -> {
                if (!ent.getVisibilityIds().isEmpty())
                    return pnInfoPaClient
                        .getOnePa(ent.getVisibilityIds().get(0).getUniqueIdentifier()) // per ora chiedo solo il primo...in futuro l'intera lista
                        .flatMap(pa -> {
                            ent.getVisibilityIds().get(0).setName(pa.getName());
                            return Mono.just(ent);
                            });   
                else
                    return Mono.just(ent);
            })         
        ;
    }

   
    public Mono<Object> rejectMandate(String mandateId, String internaluserId) {
        return mandateDao.rejectMandate(internaluserId, mandateId);
    }

    
    public Mono<Object> revokeMandate(String mandateId, String internaluserId) {
        return mandateDao.revokeMandate(internaluserId, mandateId);
    }

     
}
