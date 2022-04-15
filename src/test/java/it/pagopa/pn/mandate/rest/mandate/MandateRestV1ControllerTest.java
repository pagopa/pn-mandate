package it.pagopa.pn.mandate.rest.mandate;

import it.pagopa.pn.mandate.mapper.MandateEntityMandateDtoMapper;
import it.pagopa.pn.mandate.mapper.UserEntityMandateCountsDtoMapper;
import it.pagopa.pn.mandate.middleware.db.MandateDaoTestIT;
import it.pagopa.pn.mandate.rest.mandate.v1.dto.InternalMandateDto;
import it.pagopa.pn.mandate.rest.mandate.v1.dto.MandateCountsDto;
import it.pagopa.pn.mandate.rest.mandate.v1.dto.MandateDto;
import it.pagopa.pn.mandate.services.mandate.v1.MandateService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;

@WebFluxTest(controllers = {MandateRestV1Controller.class})
@Import({MandateEntityMandateDtoMapper.class, UserEntityMandateCountsDtoMapper.class})
class MandateRestV1ControllerTest {

    public static final String PN_PAGOPA_USER_ID = "x-pagopa-pn-cx-type";
    public static final String PN_PAGOPA_CX_TYPE = "x-pagopa-pn-cx-type";
    @Autowired
    WebTestClient webTestClient;

    @Autowired
    MandateEntityMandateDtoMapper mapper;

    @Autowired
    UserEntityMandateCountsDtoMapper mappercount;

    @MockBean
    private MandateService mandateService;


    @Test
    void countMandatesByDelegate() {
        //Given
        String url = "/mandate/api/v1/count-by-delegate";
        MandateCountsDto mandateCount = new MandateCountsDto();
        mandateCount.setValue(5);

        //When
        Mockito.when( mandateService.countMandatesByDelegate( Mockito.any(), Mockito.any() ))
                .thenReturn(Mono.just(mandateCount));

        //Then
        webTestClient.get()
                .uri(url)
                .accept(MediaType.APPLICATION_JSON)
                .header(PN_PAGOPA_USER_ID, "internaluserid1234")
                .header(PN_PAGOPA_CX_TYPE, "PF")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void acceptMandate() {
        //Given
        String url = "/mandate/api/v1/mandate/{mandateId}/accept"
                .replace("{mandateId}", "mandateId12345");

        //When
        Mockito.when( mandateService.acceptMandate( Mockito.any(), Mockito.any() , Mockito.any()))
                .thenReturn(Mono.empty());

        //Then
        webTestClient.patch()
                .uri(url)
                .accept(MediaType.APPLICATION_JSON)
                .header( PN_PAGOPA_USER_ID, "internaluserid1234")
                .header( PN_PAGOPA_CX_TYPE, "PF")
                .exchange()
                .expectStatus().isOk();
    }


    @Test
    void createMandate() {
        //Given
        String url = "/mandate/api/v1/mandate";

        MandateDto dto = mapper.toDto(MandateDaoTestIT.newMandate(true));

        //When
        Mockito.when( mandateService.createMandate( Mockito.any(), Mockito.any() , Mockito.anyBoolean()))
                .thenReturn(Mono.just(dto));

        //Then
        webTestClient.post()
                .uri(url)
                .accept(MediaType.APPLICATION_JSON)
                .header( PN_PAGOPA_USER_ID, "internaluserid1234")
                .header( PN_PAGOPA_CX_TYPE, "PF")
                .exchange()
                .expectStatus().isCreated()
                .expectBody();
    }

    @Test
    void listMandatesByDelegate1() {
        //Given
        String url = "/mandate/api/v1/mandates-by-delegate";
        List<MandateDto> mandateDtoList = Collections.singletonList(
                mapper.toDto(MandateDaoTestIT.newMandate(true))
        );

        //When
        Mockito.when( mandateService.listMandatesByDelegate( Mockito.any(), Mockito.any() ))
                .thenReturn(Flux.fromIterable(mandateDtoList ));

        //Then
        webTestClient.get()
                .uri(url)
                .accept(MediaType.APPLICATION_JSON)
                .header( PN_PAGOPA_USER_ID, "internaluserid1234")
                .header( PN_PAGOPA_CX_TYPE, "PF")
                .exchange()
                .expectStatus().isOk().expectBodyList(MandateDto.class);
    }

    @Test
    void listMandatesByDelegator1() {
        //Given
        String url = "/mandate/api/v1/mandates-by-delegator";
        List<MandateDto> mandateDtoList = Collections.singletonList(
                mapper.toDto(MandateDaoTestIT.newMandate(true))
        );

        //When
        Mockito.when( mandateService.listMandatesByDelegator( Mockito.any()))
                .thenReturn(Flux.fromIterable(mandateDtoList ));

        //Then
        webTestClient.get()
                .uri(url)
                .accept(MediaType.APPLICATION_JSON)
                .header( PN_PAGOPA_USER_ID, "internaluserid1234")
                .header( PN_PAGOPA_CX_TYPE, "PF")
                .exchange()
                .expectStatus().isOk().expectBodyList(MandateDto.class);
    }

    @Test
    void rejectMandate() {
        //Given
        String url = "/mandate/api/v1/mandate/{mandateId}/reject"
                .replace("{mandateId}", "mandateId12345");

        //When
        Mockito.when( mandateService.rejectMandate( Mockito.any(), Mockito.any()))
                .thenReturn(Mono.empty());

        //Then
        webTestClient.patch()
                .uri(url)
                .accept(MediaType.APPLICATION_JSON)
                .header( PN_PAGOPA_USER_ID, "internaluserid1234")
                .header( PN_PAGOPA_CX_TYPE, "PF")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void revokeMandate() {
        //Given
        String url = "/mandate/api/v1/mandate/{mandateId}/revoke"
                .replace("{mandateId}", "mandateId12345");

        //When
        Mockito.when( mandateService.revokeMandate( Mockito.any(), Mockito.any()))
                .thenReturn(Mono.empty());

        //Then
        webTestClient.patch()
                .uri(url)
                .accept(MediaType.APPLICATION_JSON)
                .header( PN_PAGOPA_USER_ID, "internaluserid1234")
                .header( PN_PAGOPA_CX_TYPE, "PF")
                .exchange()
                .expectStatus().isOk();
    }
}