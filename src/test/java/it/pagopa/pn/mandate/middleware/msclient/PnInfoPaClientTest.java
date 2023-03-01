package it.pagopa.pn.mandate.middleware.msclient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.pagopa.pn.mandate.microservice.msclient.generated.infopa.v1.dto.PaSummaryDto;
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
import java.util.List;
import java.util.Map;

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
    void getManyPa() {
        //Given
        String id1 = "12345678";
        String id2 = "22345678";
        String nome1 = "nome1";
        String nome2 = "nome2";
        List<String> ids = List.of(id1, id2);

        PaSummaryDto paSummaryDto1 = new PaSummaryDto();
        paSummaryDto1.setId(id1);
        paSummaryDto1.setName(nome1);
        PaSummaryDto paSummaryDto2 = new PaSummaryDto();
        paSummaryDto2.setId(id2);

        paSummaryDto2.setName(nome2);

        List<PaSummaryDto> res = List.of(paSummaryDto1, paSummaryDto2);
        byte[] responseBodyBites = new byte[0];

        ObjectMapper mapper = new ObjectMapper();
        mapper.writerFor( List.class );
        try {
            responseBodyBites = mapper.writeValueAsBytes( res );
        } catch ( JsonProcessingException e ){
            e.printStackTrace();
        }

        new MockServerClient("localhost", 9999)
        .when(request()
                    .withMethod("GET")
                    .withQueryStringParameters(Map.of("id", List.of(id1, id2)))
                    .withPath("/ext-registry-private/pa/v1/activated-on-pn"))
            .respond(response()
                    .withContentType(MediaType.APPLICATION_JSON)
                    .withBody(responseBodyBites)
                    .withContentType(MediaType.APPLICATION_JSON)
                    .withStatusCode(200));

        //When
        List<PaSummaryDto> result = pnInfoPaClient.getManyPa(ids).collectList().block(Duration.ofMillis(3000));

        //Then
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(id1, result.get(0).getId());
        assertEquals(id2, result.get(1).getId());
        assertEquals(nome1, result.get(0).getName());
        assertEquals(nome2, result.get(1).getName());
    }
}