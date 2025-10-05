package it.pagopa.pn.mandate.mapper;

import it.pagopa.pn.ciechecker.model.CieValidationData;
import it.pagopa.pn.mandate.appio.generated.openapi.server.v1.dto.CIEValidationData;
import it.pagopa.pn.mandate.appio.generated.openapi.server.v1.dto.MRTDData;
import it.pagopa.pn.mandate.appio.generated.openapi.server.v1.dto.NISData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CieCheckerAdapterMapperTest {

    private final CieCheckerAdapterMapper mapper = new CieCheckerAdapterMapper();

    @Test
    @DisplayName("Should correctly map valid input data to library DTO")
    void mapToLibDtoWithValidInputShouldMapFieldsCorrectly() {
        NISData nisData = new NISData();
        nisData.setNis("dGVzdA==");
        nisData.setSod("c29k");
        nisData.setPubKey("cHVia2V5");
        MRTDData mrtdData = new MRTDData();
        mrtdData.setSod("bXJ0ZFNvZA==");
        mrtdData.setDg1("ZGcx");
        mrtdData.setDg11("ZGcxMQ==");
        CIEValidationData input = new CIEValidationData();
        input.setNisData(nisData);
        input.setMrtdData(mrtdData);
        input.setSignedNonce("c2lnbmVkTm9uY2U=");

        String nonce = "nonceValue";
        String delegatorTaxId = "ABCDEF12G34H567I";

        CieValidationData result = mapper.mapToLibDto(input, nonce, delegatorTaxId);

        assertNotNull(result);
        assertEquals(nonce, result.getNonce());
        assertEquals(delegatorTaxId, result.getCodFiscDelegante());
        assertArrayEquals("test".getBytes(), result.getCieIas().getNis());
        assertArrayEquals("sod".getBytes(), result.getCieIas().getSod());
        assertArrayEquals("pubkey".getBytes(), result.getCieIas().getPublicKey());
        assertArrayEquals("mrtdSod".getBytes(), result.getCieMrtd().getSod());
        assertArrayEquals("dg1".getBytes(), result.getCieMrtd().getDg1());
        assertArrayEquals("dg11".getBytes(), result.getCieMrtd().getDg11());
        assertArrayEquals("signedNonce".getBytes(), result.getSignedNonce());
    }
}
