package it.pagopa.pn.mandate.rest.mandate;

import it.pagopa.pn.mandate.rest.mandate.v1.api.MandatePrivateServiceApi;
import it.pagopa.pn.mandate.rest.mandate.v1.dto.InternalMandateDto;
import it.pagopa.pn.mandate.services.mandate.v1.MandatePrivateService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
public class MandatePrivateRestV1Controller  implements MandatePrivateServiceApi   {

    MandatePrivateService mandateService;
    

    public MandatePrivateRestV1Controller(MandatePrivateService mandateService) {
        this.mandateService = mandateService;
    }


    @Override
    public Mono<ResponseEntity<Flux<InternalMandateDto>>> listMandatesByDelegate(String internaluserId,
            String mandateId,
            ServerWebExchange exchange) {
        
        return  mandateService.listMandatesByDelegate(internaluserId, mandateId)
            .collectList()     
            .map(m -> ResponseEntity.status(HttpStatus.OK).body(Flux.fromIterable(m)));     
    }

    @Override
    public Mono<ResponseEntity<Flux<InternalMandateDto>>> listMandatesByDelegator(String internaluserId, String mandateId,  final ServerWebExchange exchange) {
        return  mandateService.listMandatesByDelegator(internaluserId, mandateId)
                .collectList()
                .map(m -> ResponseEntity.status(HttpStatus.OK).body(Flux.fromIterable(m)));

    }
}
