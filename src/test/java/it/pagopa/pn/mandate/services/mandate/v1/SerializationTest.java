package it.pagopa.pn.mandate.services.mandate.v1;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.pagopa.pn.mandate.appio.generated.openapi.server.v1.dto.Problem;
import it.pagopa.pn.mandate.appio.generated.openapi.server.v1.dto.ProblemError;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;

import static org.junit.jupiter.api.Assertions.assertFalse;

@ActiveProfiles("test")
class SerializationTest {

    @Test
    public void testNullFieldIsOmitted() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        // Se stai usando la configurazione globale di Spring, dovresti configurare il mapper:
       // mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

        it.pagopa.pn.mandate.appio.generated.openapi.server.v1.dto.Problem oggetto = new Problem();
        it.pagopa.pn.mandate.appio.generated.openapi.server.v1.dto.ProblemError e = new ProblemError();
        oggetto.setTitle("Valore");
        List<ProblemError> lE = new ArrayList<>();
        lE.add(e);
        e.setElement(null);
       oggetto.setErrors(lE); // Questo è il campo che vogliamo omettere

        String json = mapper.writeValueAsString(oggetto);

        System.out.println("JSON Risultante: " + json);

        // Verifica che la chiave "campoNullo" non sia presente nel testo JSON
        assertFalse(json.contains("campoNullo"), "Il campo nullo dovrebbe essere omesso!");
    }
}