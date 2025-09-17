package it.pagopa.pn.mandate.rest.mandate;

import it.pagopa.pn.mandate.appio.generated.openapi.server.v1.dto.CxTypeAuthFleet;
import it.pagopa.pn.mandate.appio.generated.openapi.server.v1.dto.MandateCreationRequest;
import it.pagopa.pn.mandate.appio.generated.openapi.server.v1.dto.MandateCreationResponse;
import it.pagopa.pn.mandate.appio.reverse.generated.openapi.server.v1.api.AppIoPnMandateCreateApi;
import it.pagopa.pn.mandate.services.mandate.v1.MandateService;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
@lombok.CustomLog
@AllArgsConstructor
public class MandateAppIoController implements AppIoPnMandateCreateApi {
    private final MandateService mandateService;

    @Override
    public Mono<ResponseEntity<MandateCreationResponse>> createIOMandate(String xPagopaPnUid, String xPagopaPnCxId, CxTypeAuthFleet xPagopaPnCxType, Mono<MandateCreationRequest> mandateCreationRequest, ServerWebExchange exchange) {
        return mandateService.createMandateAppIo(xPagopaPnUid,xPagopaPnCxId,xPagopaPnCxType,mandateCreationRequest)
                .map(response ->ResponseEntity.status(HttpStatus.CREATED).body(response));
    }
}
