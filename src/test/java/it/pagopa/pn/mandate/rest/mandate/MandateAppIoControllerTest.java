package it.pagopa.pn.mandate.rest.mandate;

import it.pagopa.pn.mandate.appio.generated.openapi.server.v1.dto.CxTypeAuthFleet;
import it.pagopa.pn.mandate.appio.generated.openapi.server.v1.dto.MandateCreationRequest;
import it.pagopa.pn.mandate.appio.generated.openapi.server.v1.dto.MandateCreationResponse;
import it.pagopa.pn.mandate.services.mandate.v1.MandateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MandateAppIoControllerTest {

    private MandateService mandateService;
    private MandateAppIoController controller;

    @BeforeEach
    void setUp() {
        mandateService = mock(MandateService.class);
        controller = new MandateAppIoController(mandateService);
    }

    @Test
    void testCreateIOMandate() {
        String cxId = "testCxId";
        String lollipopUserName = "John";
        String lollipopUserFamilyName = "Doe";
        CxTypeAuthFleet cxType = CxTypeAuthFleet.PA;
        MandateCreationRequest request = new MandateCreationRequest();
        MandateCreationResponse response = new MandateCreationResponse();

        when(mandateService.createMandateAppIo(anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(Mono.just(response));

        Mono<ResponseEntity<MandateCreationResponse>> result = controller.createIOMandate(
                cxId, cxType, lollipopUserName, lollipopUserFamilyName, Mono.just(request), mock(ServerWebExchange.class)
        );

        StepVerifier.create(result)
                .expectNextMatches(entity -> entity.getStatusCode().is2xxSuccessful() && entity.getBody() == response)
                .verifyComplete();
    }
}
