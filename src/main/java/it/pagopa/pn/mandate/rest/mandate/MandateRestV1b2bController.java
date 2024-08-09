package it.pagopa.pn.mandate.rest.mandate;

import it.pagopa.pn.mandate.generated.openapi.server.v1.api.MandateServiceB2bApi;
import it.pagopa.pn.mandate.generated.openapi.server.v1.dto.CxTypeAuthFleet;
import it.pagopa.pn.mandate.generated.openapi.server.v1.dto.MandateDtoRequest;
import it.pagopa.pn.mandate.generated.openapi.server.v1.dto.MandateDtoResponse;
import it.pagopa.pn.mandate.services.mandate.v1.MandateService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@lombok.CustomLog
public class MandateRestV1b2bController implements MandateServiceB2bApi {

    private final MandateService mandateService;

    public MandateRestV1b2bController(MandateService mandateService) {
        this.mandateService = mandateService;
    }


    @Override
    public Mono<ResponseEntity<MandateDtoResponse>> createMandateB2b(String xPagopaPnUid,
                                                                     String xPagopaPnCxId,
                                                                     CxTypeAuthFleet xPagopaPnCxType,
                                                                     List<String> groups,
                                                                     String role,
                                                                     Mono<MandateDtoRequest> mandateDtoRequest,
                                                                     final ServerWebExchange exchange) {

        return mandateService.createMandateB2b(mandateDtoRequest, xPagopaPnUid, xPagopaPnCxId, xPagopaPnCxType, groups, role)
                .map(m -> ResponseEntity.status(HttpStatus.CREATED).body(m));
    }


}
