package it.pagopa.pn.mandate.services.mandate.v1;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service; 
import org.springframework.web.server.ServerWebExchange;

import it.pagopa.pn.mandate.rest.mandate.v1.api.MandateServiceApiDelegate;
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
public class MandateService implements MandateServiceApiDelegate {
   
    private static Map<String, MandateDto> mockdb = new HashMap<>();

    public MandateService() {       
    }
    
    
    @Override
    public Mono<ResponseEntity<Object>> acceptMandate(String mandateId, Mono<AcceptRequestDto> acceptRequestDto,
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
    
                return Mono.just(ResponseEntity.status(HttpStatus.NO_CONTENT).body(null));
            }catch(Exception ex)
            {
                throw Exceptions.propagate(ex);
            }            
        },
        (m, r) -> r)
        ;              
                
    }

    @Override
    public Mono<ResponseEntity<MandateCountsDto>> countMandatesByDelegate(String status, ServerWebExchange exchange) {
        return MandateServiceApiDelegate.super.countMandatesByDelegate(status, exchange);
    }

    @Override
    public Mono<ResponseEntity<MandateDto>> createMandate(Mono<MandateDto> mandateDto, ServerWebExchange exchange) {
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

            return Mono.just(ResponseEntity.status(HttpStatus.CREATED).body(m));
        },
        (m, r) -> r);
        
    }

    @Override
    public Mono<ResponseEntity<Flux<MandateDto>>> listMandatesByDelegate1(String status, ServerWebExchange exchange) {
        List<MandateDto> mm = new ArrayList<>();
        for (MandateDto mandateDto : mockdb.values()) {
            if (mandateDto.getDelegator() != null)
            {
                if (status == null || mandateDto.getStatus().getValue().equals(status))
                    mm.add(mandateDto);
            }
        }
        
        return Mono.just(ResponseEntity.ok().body(
            Flux.fromIterable(mm)
        ));
    }

    @Override
    public Mono<ResponseEntity<Flux<MandateDto>>> listMandatesByDelegator1(ServerWebExchange exchange) {
        List<MandateDto> mm = new ArrayList<>();
        for (MandateDto mandateDto : mockdb.values()) {
            if (mandateDto.getDelegate() != null)
            {
                mm.add(mandateDto);
            }
        }
        
        return Mono.just(ResponseEntity.ok().body(
            Flux.fromIterable(mm)
        ));
    }

    @Override
    public Mono<ResponseEntity<Object>> rejectMandate(String mandateId, ServerWebExchange exchange) {
        if (mockdb.containsKey(mandateId))
        {
          mockdb.remove(mandateId);
        }
        return  Mono.just(ResponseEntity.status(HttpStatus.NO_CONTENT).body(null));
    }

    @Override
    public Mono<ResponseEntity<Object>> revokeMandate(String mandateId, ServerWebExchange exchange) {
        if (mockdb.containsKey(mandateId))
        {
          mockdb.remove(mandateId);
        }
        return  Mono.just(ResponseEntity.status(HttpStatus.NO_CONTENT).body(null));
    }

     
}
