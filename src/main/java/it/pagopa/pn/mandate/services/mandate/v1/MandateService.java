package it.pagopa.pn.mandate.services.mandate.v1;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import org.springframework.stereotype.Service; 

import it.pagopa.pn.mandate.mapper.MandateEntityMandateDtoMapper;
import it.pagopa.pn.mandate.mapper.UserEntityMandateCountsDtoMapper;
import it.pagopa.pn.mandate.microservice.client.datavault.v1.dto.BaseRecipientDtoDto;
import it.pagopa.pn.mandate.microservice.client.datavault.v1.dto.MandateDtoDto;
import it.pagopa.pn.mandate.middleware.db.MandateDao;
import it.pagopa.pn.mandate.middleware.db.UserDao;
import it.pagopa.pn.mandate.middleware.db.entities.MandateEntity;
import it.pagopa.pn.mandate.middleware.microservice.PnDataVaultClient;
import it.pagopa.pn.mandate.middleware.microservice.PnInfoPaClient;
import it.pagopa.pn.mandate.rest.mandate.v1.dto.AcceptRequestDto;
import it.pagopa.pn.mandate.rest.mandate.v1.dto.MandateCountsDto;
import it.pagopa.pn.mandate.rest.mandate.v1.dto.MandateDto;
import it.pagopa.pn.mandate.rest.mandate.v1.dto.UserDto;
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
    private PnDataVaultClient pnDatavaultClient;

    public MandateService(MandateDao mandateDao, UserDao userDao, MandateEntityMandateDtoMapper mandateEntityMandateDtoMapper,
        UserEntityMandateCountsDtoMapper userEntityMandateCountsDtoMapper, PnInfoPaClient pnInfoPaClient, PnDataVaultClient pnDatavaultClient) {
        this.mandateDao = mandateDao;
        this.userDao =userDao;
        this.mandateEntityMandateDtoMapper = mandateEntityMandateDtoMapper;
        this.userEntityMandateCountsDtoMapper = userEntityMandateCountsDtoMapper;
        this.pnInfoPaClient = pnInfoPaClient;
        this.pnDatavaultClient= pnDatavaultClient;
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
                    return userEntityMandateCountsDtoMapper.toMonoDto(Mono.just(entity));
                });
    }

    
    public Mono<MandateDto> createMandate(Mono<MandateDto> mandateDto, String internaluserId) {
        return mandateDto
        .flatMap(dto -> mandateEntityMandateDtoMapper.toMonoEntity(Mono.just(dto)))
        .map(entity -> {
                entity.setDelegator(internaluserId);
                return entity;
        })
        .flatMap(mandate -> {
            return mandateDao.createMandate(mandate);            
        })
        .flatMap(r -> {            
            r.setDelegator(null);             
            return mandateEntityMandateDtoMapper.toMonoDto(Mono.just(r));
        })
        
        ;
         
        
    }
    
    public Flux<MandateDto> listMandatesByDelegate(String status, String internaluserId) {
        // il metodo si occupa di tornare la lista delle deleghe per delegato.
        // Devo quindi:
        // (1) recuperare la lista delle entity da db
        // (2) pulisco le info provenienti da db dalle informazioni che non devo tornare (in questo caso, il validationcode)
        // (3) risolvere internalId del delegante (il delegato sono io, e non serve popolarlo) nel relativo microservizio
        // (4) risolvere eventuali deleghe con PA impostata, andando a recuperare il nome (da db recupero solo l'id) nel relativo microservizio
       
        Optional<String> optstatus = Optional.ofNullable(status).filter(Predicate.not(String::isEmpty));
        return mandateDao.listMandatesByDelegate(internaluserId, optstatus)            
                .map(ent -> {         
                    ent.setValidationcode(null);
                    return ent;        
                })                 
                .collectList()                                                        // (3)
                .zipWhen(entities -> {
                            // genero la lista degli id deleganti
                            List<String> internaluserIds = new ArrayList<String>();
                            for(MandateEntity ent : entities)
                            {
                                internaluserIds.add(ent.getDelegator());
                            }                        
                            // ritorno la lista
                            return this.pnDatavaultClient.getRecipientDenominationByInternalId(internaluserIds)
                                .collectMap(BaseRecipientDtoDto::getInternalId, BaseRecipientDtoDto::getDenomination);
                    },
                    (entities, userinfosdtos) -> {
                        List<MandateDto> dtos = new ArrayList<>();

                        for(MandateEntity ent : entities)
                        {
                            //if (userinfosdtos)
                            /*for(BaseRecipientDtoDto userinfosdto : userinfosdtos)
                            {
                                if (ent.getDelegator().equals(userinfosdto.getInternalId()))
                                {
                                    UserDto user = new UserDto();
                                    user.setCompanyName(userinfosdto.getDenomination());
                                    user.setFirstName(userinfosdto.getInfo().getDestName());
                                    user.setLastName(userinfosdto.getInfo().getDestSurname());
                                    user.setPerson(userinfosdto.getInfo().getDestBusinessName() == null);
                                    dto.delegate(user);
                                }
                            }
                            dtos.add(mandateEntityMandateDtoMapper.toDto(ent));*/
                        }
                        return dtos;
                    })
                .flatMapMany(Flux::fromIterable)
                //.flatMap(ent -> mandateEntityMandateDtoMapper.toDto(Mono.just(ent)))                  
                .flatMap(ent -> {                                           // (4)
                    if (!ent.getVisibilityIds().isEmpty())
                        return pnInfoPaClient
                            .getOnePa(ent.getVisibilityIds().get(0).getUniqueIdentifier()) // per ora chiedo solo il primo...in futuro l'intera lista
                            .flatMap(pa -> {
                                ent.getVisibilityIds().get(0).setName(pa.getName());
                                return Mono.just(ent);
                                });   
                    else
                        return Mono.just(ent);
                })   ;

            
    }

    
    public Flux<MandateDto> listMandatesByDelegator(String internaluserId) {
        // il metodo si occupa di tornare la lista delle deleghe per delegato.
        // Devo quindi:
        // (1) recuperare la lista delle entity da db
        // (2) converto entity in dto
        // (3) recupero le info dei DELEGATI, eseguendo una richiesta con la lista degli id delle deleghe
        // (4) risolvere eventuali deleghe con PA impostata, andando a recuperare il nome (da db recupero solo l'id) nel relativo microservizio
        
        Optional<String> optstatus = Optional.ofNullable(null);
        return mandateDao.listMandatesByDelegator(internaluserId, optstatus)    // (1)
            .flatMap(ent -> mandateEntityMandateDtoMapper.toMonoDto(Mono.just(ent)))  // (2)
            .collectList()                                                        // (3)
            .zipWhen(dtos -> {
                        // genero la lista degli id delega
                        List<String> mandateIds = new ArrayList<String>();
                        for(MandateDto dto : dtos)
                        {
                            mandateIds.add(dto.getMandateId());
                        }                        
                        // ritorno la lista
                        return this.pnDatavaultClient.getMandatesByIds(mandateIds)
                            .collectMap(MandateDtoDto::getMandateId, MandateDtoDto::getInfo);
                },
                (dtos, userinfosdtos) -> {
                    
                    //((Map<String, AddressAndDenominationDtoDto>)userinfosdtos).

                    for(MandateDto dto : dtos)
                    {
                        //if (userinfosdtos.)
                        /*for(MandateDtoDto userinfosdto : userinfosdtos)
                        {
                            if (dto.getMandateId().equals(userinfosdto.getMandateId()))
                            {
                                UserDto user = dto.getDelegate();
                                user.setCompanyName(userinfosdto.getInfo().getDestBusinessName());
                                user.setEmail(userinfosdto.getInfo().getValue());
                                user.setFirstName(userinfosdto.getInfo().getDestName());
                                user.setLastName(userinfosdto.getInfo().getDestSurname());                                
                                //user.setDisplayName()
                            }
                        }*/
                    }
                    return dtos;
                })
            .flatMapMany(Flux::fromIterable)
            .flatMap(ent -> {                                           // (4)
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
