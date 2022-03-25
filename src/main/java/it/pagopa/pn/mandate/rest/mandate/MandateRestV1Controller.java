package it.pagopa.pn.mandate.rest.mandate;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import it.pagopa.pn.mandate.rest.mandate.v1.api.MandateServiceApi;
import it.pagopa.pn.mandate.rest.mandate.v1.dto.AcceptRequestDto;
import it.pagopa.pn.mandate.rest.mandate.v1.dto.MandateCountsDto;
import it.pagopa.pn.mandate.rest.mandate.v1.dto.MandateDto;
import it.pagopa.pn.mandate.services.mandate.v1.MandateService;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
public class MandateRestV1Controller  implements MandateServiceApi   {

    // Header utente pn-pagopa-user-id
    private static final String HEADER_INTERNAL_USER_ID = "pn-pagopa-user-id";
    // Header tipologia di utente pn-pagopa-cx-type PF/PG (per ora non usato dalla logica)
    //private static final String HEADER_CX_TYPE = "pn-pagopa-cx-type";
    MandateService mandateService;    
    

    public MandateRestV1Controller(MandateService mandateService) {
        this.mandateService = mandateService;
    }

    @Override
    public Mono<ResponseEntity<Object>> acceptMandate(String mandateId, Mono<AcceptRequestDto> acceptRequestDto,
            ServerWebExchange exchange) {
                
        String internaluserId = getInternaluserIdFromHeaders(exchange.getRequest());
        return  mandateService.acceptMandate(mandateId, acceptRequestDto, internaluserId)
            .map(m -> ResponseEntity.status(HttpStatus.CREATED).body(m));    
    }

    @Override
    public Mono<ResponseEntity<MandateCountsDto>> countMandatesByDelegate(String status, ServerWebExchange exchange) {
        String internaluserId = getInternaluserIdFromHeaders(exchange.getRequest());
        return  mandateService.countMandatesByDelegate(status, internaluserId)
            .map(m -> ResponseEntity.status(HttpStatus.OK).body(m));    
    }

    @Override
    public Mono<ResponseEntity<MandateDto>> createMandate(Mono<MandateDto> mandateDto, ServerWebExchange exchange) {     
        String internaluserId = getInternaluserIdFromHeaders(exchange.getRequest());
        return  mandateService.createMandate(mandateDto, internaluserId)
            .map(m ->  ResponseEntity.status(HttpStatus.CREATED).body(m));
         
    }

    @Override
    public Mono<ResponseEntity<Flux<MandateDto>>> listMandatesByDelegate1(String status, ServerWebExchange exchange) {
        String internaluserId = getInternaluserIdFromHeaders(exchange.getRequest());

        return  mandateService.listMandatesByDelegate(status, internaluserId)
            .collectList()     
            .map(m -> ResponseEntity.status(HttpStatus.OK).body(Flux.fromIterable(m)));     
    }

    @Override
    public Mono<ResponseEntity<Flux<MandateDto>>> listMandatesByDelegator1(ServerWebExchange exchange) {
        String internaluserId = getInternaluserIdFromHeaders(exchange.getRequest());
        return  mandateService.listMandatesByDelegator(internaluserId)
            .collectList()     
            .map(m -> ResponseEntity.status(HttpStatus.OK).body(Flux.fromIterable(m)));     
    }

    @Override
    public Mono<ResponseEntity<Object>> rejectMandate(String mandateId, ServerWebExchange exchange) {
        String internaluserId = getInternaluserIdFromHeaders(exchange.getRequest());
        return  mandateService.rejectMandate(mandateId, internaluserId)
        .map(m -> ResponseEntity.status(HttpStatus.NO_CONTENT).body(null));     
    }

    @Override
    public Mono<ResponseEntity<Object>> revokeMandate(String mandateId, ServerWebExchange exchange) {
        String internaluserId = getInternaluserIdFromHeaders(exchange.getRequest());
        return  mandateService.revokeMandate(mandateId, internaluserId)
        .map(m -> ResponseEntity.status(HttpStatus.NO_CONTENT).body(null));     
    }
    

    private static String getInternaluserIdFromHeaders(ServerHttpRequest req)
    {
        return  req.getHeaders().getFirst(HEADER_INTERNAL_USER_ID);
    }

    /*private static String getUserTypeFromHeaders(ServerHttpRequest req)
    {
        return  req.getHeaders().getFirst(HEADER_CX_TYPE);
    }*/
}
