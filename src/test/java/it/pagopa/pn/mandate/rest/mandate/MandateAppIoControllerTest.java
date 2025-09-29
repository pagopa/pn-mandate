package it.pagopa.pn.mandate.rest.mandate;

import it.pagopa.pn.mandate.appio.generated.openapi.server.v1.dto.*;
import it.pagopa.pn.mandate.services.mandate.v1.MandateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

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
        String uid = "testUid";
        String cxId = "testCxId";
        CxTypeAuthFleet cxType = CxTypeAuthFleet.PA;
        String xPagopaCxTaxid = "cxTaxId";
        List<String> xPagopaPnCxGroups = List.of("group1");
        MandateCreationRequest request = new MandateCreationRequest();
        MandateCreationResponse response = new MandateCreationResponse();

        when(mandateService.createMandateAppIo(anyString(), anyString(), any(), any()))
                .thenReturn(Mono.just(response));

        Mono<ResponseEntity<MandateCreationResponse>> result = controller.createIOMandate(
                uid, cxId, cxType, xPagopaCxTaxid, xPagopaPnCxGroups, Mono.just(request), mock(ServerWebExchange.class)
        );

        StepVerifier.create(result)
                .expectNextMatches(entity -> entity.getStatusCode().is2xxSuccessful() && entity.getBody() == response)
                .verifyComplete();
    }


    @Test
    void testAcceptIOMandate() {
        String cxId = "testCxId";
        CxTypeAuthFleet cxType = CxTypeAuthFleet.PA;
        String cxTaxId = "testTaxId";
        String mandateId = "mandateId";
        List<String> cxGroups = List.of("group1", "group2");
        String cxRole = "role";
        CIEValidationData cieValidationData = new CIEValidationData();

        when(mandateService.acceptMandateAppIo(anyString(), any(), anyString(), anyString(), anyList(), anyString(), any()))
                .thenReturn(Mono.empty());

        Mono<ResponseEntity<Void>> result = controller.acceptIOMandate(
                cxId, cxType, cxTaxId, mandateId, cxGroups, cxRole, Mono.just(cieValidationData), mock(ServerWebExchange.class)
        );

        StepVerifier.create(result)
                .expectNextMatches(entity -> entity.getStatusCode().is2xxSuccessful() && entity.getStatusCodeValue() == 204)
                .verifyComplete();

        verify(mandateService).acceptMandateAppIo(eq(cxId), eq(cxType), eq(cxTaxId), eq(mandateId), eq(cxGroups), eq(cxRole), any());
    }
}
