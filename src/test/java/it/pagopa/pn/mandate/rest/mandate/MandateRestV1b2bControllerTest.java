package it.pagopa.pn.mandate.rest.mandate;

import it.pagopa.pn.mandate.generated.openapi.server.v1.dto.MandateDtoResponse;
import it.pagopa.pn.mandate.mapper.MandateEntityMandateDtoB2bMapper;
import it.pagopa.pn.mandate.middleware.db.MandateDaoIT;
import it.pagopa.pn.mandate.services.mandate.v1.MandateService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@WebFluxTest(controllers = {MandateRestV1b2bController.class})
@Import({MandateEntityMandateDtoB2bMapper.class})
class MandateRestV1b2bControllerTest {

    public static final String PN_PAGOPA_USER_ID = "x-pagopa-pn-uid";
    public static final String PN_PAGOPA_CX_TYPE = "x-pagopa-pn-cx-type";
    public static final String PN_PAGOPA_CX_ID = "x-pagopa-pn-cx-id";
    public static final String PN_PAGOPA_CX_ROLE = "x-pagopa-pn-cx-role";

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private MandateEntityMandateDtoB2bMapper mapper;

    @MockBean
    private MandateService mandateService;


    @Test
    void createMandateB2b() {
        //Given
        String url = "/mandate-b2b/api/v1/mandate";

        MandateDtoResponse dto = mapper.toDto(MandateDaoIT.newMandate(true));

        //When
        Mockito.when(mandateService.createMandateB2b(Mockito.any(), Mockito.any(), Mockito.anyString(), Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(Mono.just(dto));

        //Then
        webTestClient.post()
                .uri(url)
                .accept(MediaType.APPLICATION_JSON)
                .header(PN_PAGOPA_CX_ID, "internaluserid1234")
                .header(PN_PAGOPA_CX_TYPE, "PF")
                .header(PN_PAGOPA_USER_ID, "userid")
                .exchange()
                .expectStatus().isCreated()
                .expectBody();
    }

}