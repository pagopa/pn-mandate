package it.pagopa.pn.mandate.services.mandate.v1;

import it.pagopa.pn.mandate.rest.mandate.v1.api.MandatePrivateServiceApi;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ServerWebExchange;

import it.pagopa.pn.mandate.rest.mandate.v1.dto.InternalMandateDto;
import it.pagopa.pn.mandate.rest.mandate.v1.dto.MandateDto.StatusEnum;
import it.pagopa.pn.mandate.rest.mock.InternalMandateDtoGenerator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class MandatePrivateService implements MandatePrivateServiceApi {

    
    public MandatePrivateService() {
        
    }

    @Override
    public Mono<ResponseEntity<Flux<InternalMandateDto>>> listMandatesByDelegate(String internaluserId,
            ServerWebExchange exchange) {
        InternalMandateDto[] res = new InternalMandateDto[2];
        res[0] = InternalMandateDtoGenerator.generate(StatusEnum.PENDING, true);
        res[1] = InternalMandateDtoGenerator.generate(StatusEnum.ACTIVE, true);

        return Mono.just(ResponseEntity.ok().body(
            Flux.fromArray(res)
        ));
    }

    @Override
    public Mono<ResponseEntity<Flux<InternalMandateDto>>> listMandatesByDelegator(String internaluserId,
            ServerWebExchange exchange) {
        InternalMandateDto[] res = new InternalMandateDto[2];
        res[0] = InternalMandateDtoGenerator.generate(StatusEnum.PENDING, true);
        res[1] = InternalMandateDtoGenerator.generate(StatusEnum.ACTIVE, true);

        return Mono.just(ResponseEntity.ok().body(
            Flux.fromArray(res)
        ));
    }
    
}
