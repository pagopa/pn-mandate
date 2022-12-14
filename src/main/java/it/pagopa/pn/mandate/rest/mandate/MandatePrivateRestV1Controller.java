package it.pagopa.pn.mandate.rest.mandate;

import it.pagopa.pn.mandate.rest.mandate.v1.api.MandatePrivateServiceApi;
import it.pagopa.pn.mandate.rest.mandate.v1.dto.CxTypeAuthFleet;
import it.pagopa.pn.mandate.rest.mandate.v1.dto.InternalMandateDto;
import it.pagopa.pn.mandate.services.mandate.v1.MandatePrivateService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
public class MandatePrivateRestV1Controller implements MandatePrivateServiceApi {

    MandatePrivateService mandateService;


    public MandatePrivateRestV1Controller(MandatePrivateService mandateService) {
        this.mandateService = mandateService;
    }


    @Override
    public Mono<ResponseEntity<Flux<InternalMandateDto>>> listMandatesByDelegate(
            CxTypeAuthFleet xPagopaPnCxType,
            String internaluserId,
            String mandateId,
            List<String> cxGroups,
            ServerWebExchange exchange) {

        return mandateService.listMandatesByDelegate(internaluserId, mandateId,xPagopaPnCxType, cxGroups)
                .collectList()
                .map(m -> ResponseEntity.status(HttpStatus.OK).body(Flux.fromIterable(m)));
    }

    @Override
    public Mono<ResponseEntity<Flux<InternalMandateDto>>> listMandatesByDelegator(
            CxTypeAuthFleet xPagopaPnCxType,
            String internaluserId,
            String mandateId,
            List<String> cxGroups,
            String cxRole,
            final ServerWebExchange exchange) {
        return mandateService.listMandatesByDelegator(internaluserId, mandateId,xPagopaPnCxType, cxGroups, cxRole)
                .collectList()
                .map(m -> ResponseEntity.status(HttpStatus.OK).body(Flux.fromIterable(m)));

    }
}
