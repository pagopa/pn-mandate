package it.pagopa.pn.mandate.mapper;

import it.pagopa.pn.ciechecker.model.CieValidationData;
import it.pagopa.pn.mandate.appio.generated.openapi.server.v1.dto.CIEValidationData;
import it.pagopa.pn.mandate.appio.generated.openapi.server.v1.dto.MRTDData;
import it.pagopa.pn.mandate.appio.generated.openapi.server.v1.dto.NISData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CieCheckerAdapterMapperTest {

    private final CieCheckerAdapterMapper mapper = new CieCheckerAdapterMapper();

    @Test
    void mapToLibDto_shouldMapAllFieldsCorrectly() {
        // Arrange
        NISData nisData = new NISData();
        nisData.setNis("nisValue");
        nisData.setSod("sodValue");
        nisData.setPubKey("pubKeyValue");

        MRTDData mrtdData = new MRTDData();
        mrtdData.setSod("mrtdSod");
        mrtdData.setDg1("mrtdDg1");
        mrtdData.setDg11("mrtdDg11");

        CIEValidationData input = new CIEValidationData();
        input.setNisData(nisData);
        input.setMrtdData(mrtdData);
        input.setSignedNonce("signedNonceValue");

        String nonce = "testNonce";

        // Act
        CieValidationData result = mapper.mapToLibDto(input, nonce);

        // Assert
        assertNotNull(result);
        assertNotNull(result.getCieIas());
        assertNotNull(result.getCieMrtd());
        assertArrayEquals("nisValue".getBytes(), result.getCieIas().getNis());
        assertArrayEquals("sodValue".getBytes(), result.getCieIas().getSod());
        assertArrayEquals("pubKeyValue".getBytes(), result.getCieIas().getPublicKey());
        assertArrayEquals("mrtdSod".getBytes(), result.getCieMrtd().getSod());
        assertArrayEquals("mrtdDg1".getBytes(), result.getCieMrtd().getDg1());
        assertArrayEquals("mrtdDg11".getBytes(), result.getCieMrtd().getDg11());
        assertArrayEquals("signedNonceValue".getBytes(), result.getSignedNonce());
        assertEquals("746573744e6f6e6365", result.getNonce()); // "testNonce" in hex
    }
}
