package it.pagopa.pn.mandate.middleware.msclient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.pagopa.pn.mandate.generated.openapi.msclient.extregselfcaregroups.v1.dto.PgGroupDto;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "pn.mandate.client_extreg_basepath=http://localhost:9999"
})
class PnExtRegPrvtClientTest {

    @Autowired
    private PnExtRegPrvtClient pnExtRegPrvtClient;

    private static ClientAndServer mockServer;

    @BeforeAll
    public static void startMockServer() {
        mockServer = ClientAndServer.startClientAndServer(9999);
    }

    @AfterAll
    public static void stopMockServer() {
        mockServer.stop();
    }

    @Test
    void getGroups() {
        // Given
        PgGroupDto pgGroupDto = new PgGroupDto();
        pgGroupDto.setId("id");
        pgGroupDto.setName("name");

        List<PgGroupDto> res = List.of(pgGroupDto);
        byte[] responseBodyBytes = new byte[0];

        ObjectMapper mapper = new ObjectMapper();
        try {
            responseBodyBytes = mapper.writeValueAsBytes(res);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }

        try (MockServerClient client = new MockServerClient("localhost", 9999)) {
            client.when(request()
                            .withMethod("GET")
                            .withPath("/ext-registry-private/pg/v1/groups-all")
                            .withHeader("x-pagopa-pn-cx-id", "cx-id"))
                    .respond(response()
                            .withContentType(MediaType.APPLICATION_JSON)
                            .withBody(responseBodyBytes)
                            .withContentType(MediaType.APPLICATION_JSON)
                            .withStatusCode(200));

            // When
            List<PgGroupDto> result = pnExtRegPrvtClient.getGroups("cx-id")
                    .collectList()
                    .block(Duration.ofMillis(3000));

            // Then
            assertNotNull(result);
            assertEquals(1, result.size());
            assertEquals("id", result.get(0).getId());
            assertEquals("name", result.get(0).getName());
        }
    }

}