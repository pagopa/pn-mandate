package it.pagopa.pn.mandate.services.mandate.v1;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service; 
import org.springframework.web.server.ServerWebExchange;

import it.pagopa.pn.mandate.mapper.MandateEntityMandateDtoMapper;
import it.pagopa.pn.mandate.mapper.UserEntityMandateCountsDtoMapper;
import it.pagopa.pn.mandate.middleware.db.MandateDao;
import it.pagopa.pn.mandate.middleware.db.UserDao;
import it.pagopa.pn.mandate.middleware.microservice.PnInfoPaClient;
import it.pagopa.pn.mandate.rest.mandate.v1.dto.AcceptRequestDto;
import it.pagopa.pn.mandate.rest.mandate.v1.dto.MandateCountsDto;
import it.pagopa.pn.mandate.rest.mandate.v1.dto.MandateDto;
import it.pagopa.pn.mandate.rest.mandate.v1.dto.MandateDto.StatusEnum; 
import it.pagopa.pn.mandate.rest.utils.InvalidVerificationCodeException;
import it.pagopa.pn.mandate.utils.DateUtils;
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
            ServerWebExchange exchange) {
        return acceptRequestDto
        .zipWhen(m -> {
            try{
                if (mockdb.containsKey(mandateId))
                {
                    if (m.getVerificationCode() == null || !m.getVerificationCode().equals(mockdb.get(mandateId).getVerificationCode()))
                        throw new InvalidVerificationCodeException();
                        
                    mockdb.get(mandateId).setStatus(StatusEnum.ACTIVE);
                }
                log.info("accepting mandate " + m);
    
                return Mono.empty();
            }catch(Exception ex)
            {
                throw Exceptions.propagate(ex);
            }            
        },
        (m, r) -> r)
        ;              
                
    }

    public Mono<MandateCountsDto> countMandatesByDelegate(String status, ServerWebExchange exchange) {
        List<MandateDto> mm = new ArrayList<>();
        for (MandateDto mandateDto : mockdb.values()) {
            if (mandateDto.getDelegate() != null)
            {
                if (status == null || mandateDto.getStatus().getValue().equals(status))
                    mm.add(mandateDto);
            }
        }

        MandateCountsDto res = new MandateCountsDto();
        res.setValue(mm.size());
        return Mono.just(res);
    }

    
    public Mono<MandateDto> createMandate(Mono<MandateDto> mandateDto, ServerWebExchange exchange) {
        return mandateDto
        .zipWhen(m -> {
            m.setMandateId(UUID.randomUUID().toString());  
            m.setDatefrom(DateUtils.formatDate(LocalDate.now().minusDays(120)));         
            mockdb.put(m.getMandateId(), m);        
            log.info("creating mandate " + m.toString());
            
            MandateDto m1 = new MandateDto();        
            m1.setMandateId(UUID.randomUUID().toString());  
            m1.setDatefrom(DateUtils.formatDate(LocalDate.now().minusDays(120)));         
            m1.setDateto(m.getDateto());
            m1.setStatus(m.getStatus());
            m1.setDelegator(m.getDelegate());
            m1.setVisibilityIds(m.getVisibilityIds());        
            mockdb.put(m1.getMandateId(), m1);       
            log.info("creating mandate " + m1.toString());

            return Mono.just(m);
        },
        (m, r) -> r);
        
    }

    
    public Flux<MandateDto> listMandatesByDelegate(String status, String internaluserId) {
       return 
            mandateDao.listMandatesByDelegate(internaluserId)
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

    
    public Flux<MandateDto> listMandatesByDelegator1(ServerWebExchange exchange) {
        List<MandateDto> mm = new ArrayList<>();
        for (MandateDto mandateDto : mockdb.values()) {
            if (mandateDto.getDelegate() != null)
            {
                mm.add(mandateDto);
            }
        }

        log.info("returning mandates by delegator count: " + mm.size());
        
        return Flux.fromIterable(mm);
    }

   
    public Mono<Object> rejectMandate(String mandateId, ServerWebExchange exchange) {
        if (mockdb.containsKey(mandateId))
        {
          mockdb.remove(mandateId);
          
          log.info("rejected mandate " + mandateId);
        }
        return  Mono.empty();
    }

    
    public Mono<Object> revokeMandate(String mandateId, ServerWebExchange exchange) {
        if (mockdb.containsKey(mandateId))
        {
          mockdb.remove(mandateId);
          log.info("revoked mandate " + mandateId);
        }
        return  Mono.empty();
    }

     
}
