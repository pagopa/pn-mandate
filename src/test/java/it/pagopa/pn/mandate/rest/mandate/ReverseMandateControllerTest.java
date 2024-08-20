package it.pagopa.pn.mandate.rest.mandate;

import it.pagopa.pn.mandate.generated.openapi.server.v1.dto.MandateDtoResponse;
import it.pagopa.pn.mandate.generated.openapi.server.v1.dto.UserDto;
import it.pagopa.pn.mandate.mapper.ReverseMandateEntityMandateDtoMapper;
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

@WebFluxTest(controllers = {ReverseMandateController.class})
@Import({ReverseMandateEntityMandateDtoMapper.class})
class ReverseMandateControllerTest {

    public static final String PN_PAGOPA_USER_ID = "x-pagopa-pn-uid";
    public static final String PN_PAGOPA_CX_TYPE = "x-pagopa-pn-cx-type";
    public static final String PN_PAGOPA_CX_ID = "x-pagopa-pn-cx-id";

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ReverseMandateEntityMandateDtoMapper mapper;

    @MockBean
    private MandateService mandateService;


    @Test
    void createReverseMandate() {
        //Given
        String url = "/mandate/api/v1/reverse-mandate";

        MandateDtoResponse dto = mapper.toDto(MandateDaoIT.newMandate(true), new UserDto(), new UserDto());

        //When
        Mockito.when(mandateService.createReverseMandate(Mockito.any(), Mockito.any(), Mockito.anyString(), Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(Mono.just(dto));

        //Then
        webTestClient.post()
                .uri(url)
                .accept(MediaType.APPLICATION_JSON)
                .header(PN_PAGOPA_CX_ID, "cxId")
                .header(PN_PAGOPA_CX_TYPE, "PG")
                .header(PN_PAGOPA_USER_ID, "uid")
                .exchange()
                .expectStatus().isCreated()
                .expectBody();
    }

}