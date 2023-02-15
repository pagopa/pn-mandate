package it.pagopa.pn.mandate.rest.mandate;

import it.pagopa.pn.mandate.rest.mandate.v1.api.MandateServiceApi;
import it.pagopa.pn.mandate.rest.mandate.v1.dto.AcceptRequestDto;
import it.pagopa.pn.mandate.rest.mandate.v1.dto.CxTypeAuthFleet;
import it.pagopa.pn.mandate.rest.mandate.v1.dto.MandateCountsDto;
import it.pagopa.pn.mandate.rest.mandate.v1.dto.MandateDto;
import it.pagopa.pn.mandate.services.mandate.v1.MandateService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
public class MandateRestV1Controller  implements MandateServiceApi   {

    MandateService mandateService;

    public MandateRestV1Controller(MandateService mandateService) {
        this.mandateService = mandateService;
    }

    @Override
    public Mono<ResponseEntity<Void>> acceptMandate(String xPagopaPnCxId, CxTypeAuthFleet xPagopaPnCxType, String mandateId, Mono<AcceptRequestDto> acceptRequestDto,
                                                    ServerWebExchange exchange) {

        return  mandateService.acceptMandate(mandateId, acceptRequestDto, xPagopaPnCxId)
            .then(Mono.just(ResponseEntity.noContent().build()));
    }

    @Override
    public Mono<ResponseEntity<MandateCountsDto>> countMandatesByDelegate(String xPagopaPnCxId, CxTypeAuthFleet xPagopaPnCxType, String status, ServerWebExchange exchange) {

        return  mandateService.countMandatesByDelegate(status, xPagopaPnCxId)
            .map(m -> ResponseEntity.status(HttpStatus.OK).body(m));    
    }

    @Override
    public Mono<ResponseEntity<MandateDto>> createMandate(String xPagopaPnUid, String xPagopaPnCxId, CxTypeAuthFleet xPagopaPnCxType, Mono<MandateDto> mandateDto,  final ServerWebExchange exchangee) {

        return  mandateService
            .createMandate(mandateDto, xPagopaPnUid, xPagopaPnCxId, (xPagopaPnCxType==null || xPagopaPnCxType.equals(CxTypeAuthFleet.PF)))
            .map(m ->  ResponseEntity.status(HttpStatus.CREATED).body(m));
         
    }

    @Override
    public Mono<ResponseEntity<Flux<MandateDto>>> listMandatesByDelegate1(String xPagopaPnCxId, CxTypeAuthFleet xPagopaPnCxType, String status, ServerWebExchange exchange) {

        return  mandateService.listMandatesByDelegate(status, xPagopaPnCxId)
            .collectList()     
            .map(m -> ResponseEntity.status(HttpStatus.OK).body(Flux.fromIterable(m)));     
    }

    @Override
    public Mono<ResponseEntity<Flux<MandateDto>>> listMandatesByDelegator1(String xPagopaPnCxId, CxTypeAuthFleet xPagopaPnCxType, ServerWebExchange exchange) {

        return  mandateService.listMandatesByDelegator(xPagopaPnCxId)
            .collectList()     
            .map(m -> ResponseEntity.status(HttpStatus.OK).body(Flux.fromIterable(m)));     
    }

    @Override
    public Mono<ResponseEntity<Void>> rejectMandate(String xPagopaPnCxId, CxTypeAuthFleet xPagopaPnCxType, String mandateId, ServerWebExchange exchange) {

        return  mandateService.rejectMandate(mandateId, xPagopaPnCxId)
            .then(Mono.just(ResponseEntity.noContent().build()));
    }

    @Override
    public Mono<ResponseEntity<Void>> revokeMandate(String xPagopaPnCxId, CxTypeAuthFleet xPagopaPnCxType, String mandateId, ServerWebExchange exchange) {

        return  mandateService.revokeMandate(mandateId, xPagopaPnCxId)
        .map(m -> ResponseEntity.noContent().build());
    }
}
