package it.pagopa.pn.mandate.middleware.msclient;

import it.pagopa.pn.mandate.microservice.msclient.generated.infopa.v1.dto.PaInfoDto;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.MediaType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "pn.mandate.client_infopa_basepath=http://localhost:9999"
})
class PnInfoPaClientTest {

    @Autowired
    private PnInfoPaClient pnInfoPaClient;

    private static ClientAndServer mockServer;

    @BeforeAll
    public static void startMockServer() {
        mockServer = startClientAndServer(9999);
    }

    @AfterAll
    public static void stopMockServer() {
        mockServer.stop();
    }

    @Test
    void getOnePa() {
        //Given
        String id = "12345678";


        new MockServerClient("localhost", 9999)
        .when(request()
                    .withMethod("GET")
                    .withPath("/ext-registry/pa/v1/activated-on-pn/{id}".replace("{id}", id)))
            .respond(response()
                    .withBody("{" +
                            "\"" + PaInfoDto.JSON_PROPERTY_ID + "\": " + "\"" + id + "\"," +
                            "\"" + PaInfoDto.JSON_PROPERTY_NAME + "\": " + "\"" + "prova pa" + "\"," +
                            "\"" + PaInfoDto.JSON_PROPERTY_TAX_ID + "\": " + "\"" + id + "\"" +
                            "}")
                    .withContentType(MediaType.APPLICATION_JSON)
                    .withStatusCode(200));

        //When
        PaInfoDto result = pnInfoPaClient.getOnePa(id).block(Duration.ofMillis(3000));

        //Then
        assertNotNull(result);
        assertEquals(id, result.getTaxId());
    }
}