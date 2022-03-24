package it.pagopa.pn.mandate.rest.mandate;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    MandateService mandateService;
    

    public MandateRestV1Controller(MandateService mandateService) {
        this.mandateService = mandateService;
    }

    @Override
    public Mono<ResponseEntity<Object>> acceptMandate(String mandateId, Mono<AcceptRequestDto> acceptRequestDto,
            ServerWebExchange exchange) {
        return  mandateService.acceptMandate(mandateId, acceptRequestDto, exchange)
            .map(m -> ResponseEntity.status(HttpStatus.CREATED).body(m));    
    }

    @Override
    public Mono<ResponseEntity<MandateCountsDto>> countMandatesByDelegate(String status, ServerWebExchange exchange) {
        return  mandateService.countMandatesByDelegate(status, exchange)
            .map(m -> ResponseEntity.status(HttpStatus.OK).body(m));    
    }

    @Override
    public Mono<ResponseEntity<MandateDto>> createMandate(Mono<MandateDto> mandateDto, ServerWebExchange exchange) {     
        return  mandateService.createMandate(mandateDto, exchange)
            .map(m ->  ResponseEntity.status(HttpStatus.CREATED).body(m));
         
    }

    @Override
    public Mono<ResponseEntity<Flux<MandateDto>>> listMandatesByDelegate1(String status, ServerWebExchange exchange) {
        String internaluserId = exchange.getRequest().getHeaders().getFirst(headerName)

        return  mandateService.listMandatesByDelegate(status, exchange)
            .collectList()     
            .map(m -> ResponseEntity.status(HttpStatus.OK).body(Flux.fromIterable(m)));     
    }

    @Override
    public Mono<ResponseEntity<Flux<MandateDto>>> listMandatesByDelegator1(ServerWebExchange exchange) {
        return  mandateService.listMandatesByDelegator1(exchange)
            .collectList()     
            .map(m -> ResponseEntity.status(HttpStatus.OK).body(Flux.fromIterable(m)));     
    }

    @Override
    public Mono<ResponseEntity<Object>> rejectMandate(String mandateId, ServerWebExchange exchange) {
        return  mandateService.rejectMandate(mandateId, exchange)
        .map(m -> ResponseEntity.status(HttpStatus.NO_CONTENT).body(null));     
    }

    @Override
    public Mono<ResponseEntity<Object>> revokeMandate(String mandateId, ServerWebExchange exchange) {
        return  mandateService.revokeMandate(mandateId, exchange)
        .map(m -> ResponseEntity.status(HttpStatus.NO_CONTENT).body(null));     
    }
    
}
