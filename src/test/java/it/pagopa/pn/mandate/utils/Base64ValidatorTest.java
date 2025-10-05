package it.pagopa.pn.mandate.utils;

import it.pagopa.pn.mandate.appio.generated.openapi.server.v1.dto.CIEValidationData;
import it.pagopa.pn.mandate.appio.generated.openapi.server.v1.dto.MRTDData;
import it.pagopa.pn.mandate.appio.generated.openapi.server.v1.dto.NISData;
import it.pagopa.pn.mandate.exceptions.PnMandateBadRequestException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Base64ValidatorTest {
    private final Base64Validator validator = new Base64Validator();

    private void checkExceptionDetails(PnMandateBadRequestException ex, String expectedDetail) {
        assertEquals(expectedDetail, ex.getProblem().getErrors().get(0).getDetail());
    }

    @Test
    @DisplayName("Should validate all fields when all are valid Base64")
    void validateCieValidationDataWithAllValidBase64ShouldPass() {
        CIEValidationData cieValidationData = new CIEValidationData();
        NISData nisData = new NISData();
        nisData.setNis("dGVzdA==");
        nisData.setSod("c29k");
        nisData.setPubKey("cHVia2V5");
        MRTDData mrtdData = new MRTDData();
        mrtdData.setSod("bXJ0ZFNvZA==");
        mrtdData.setDg1("ZGcx");
        mrtdData.setDg11("ZGcxMQ==");
        cieValidationData.setNisData(nisData);
        cieValidationData.setMrtdData(mrtdData);
        cieValidationData.setSignedNonce("c2lnbmVkTm9uY2U=");

        assertDoesNotThrow(() -> validator.validateCieValidationData(cieValidationData));
    }

    @Test
    @DisplayName("Should throw exception when a field is not valid Base64")
    void validateCieValidationDataWithInvalidBase64ShouldThrow() {
        CIEValidationData cieValidationData = new CIEValidationData();
        NISData nisData = new NISData();
        nisData.setNis("not_base64");
        nisData.setSod("c29k");
        nisData.setPubKey("cHVia2V5");
        MRTDData mrtdData = new MRTDData();
        mrtdData.setSod("bXJ0ZFNvZA==");
        mrtdData.setDg1("ZGcx");
        mrtdData.setDg11("ZGcxMQ==");
        cieValidationData.setNisData(nisData);
        cieValidationData.setMrtdData(mrtdData);
        cieValidationData.setSignedNonce("c2lnbmVkTm9uY2U=");

        PnMandateBadRequestException ex = assertThrows(PnMandateBadRequestException.class, () -> validator.validateCieValidationData(cieValidationData));
        checkExceptionDetails(ex, "Invalid Base64 encoding in field: NIS");
    }

    @Test
    @DisplayName("Should throw exception when a field is null")
    void validateCieValidationDataWithNullFieldShouldThrow() {
        CIEValidationData cieValidationData = new CIEValidationData();
        NISData nisData = new NISData();
        nisData.setNis(null);
        nisData.setSod("c29k");
        nisData.setPubKey("cHVia2V5");
        MRTDData mrtdData = new MRTDData();
        mrtdData.setSod("bXJ0ZFNvZA==");
        mrtdData.setDg1("ZGcx");
        mrtdData.setDg11("ZGcxMQ==");
        cieValidationData.setNisData(nisData);
        cieValidationData.setMrtdData(mrtdData);
        cieValidationData.setSignedNonce("c2lnbmVkTm9uY2U=");

        PnMandateBadRequestException ex = assertThrows(PnMandateBadRequestException.class, () -> validator.validateCieValidationData(cieValidationData));
        checkExceptionDetails(ex, "Missing or empty field: NIS");
    }

    @Test
    @DisplayName("Should throw exception when a field is empty string")
    void validateCieValidationDataWithEmptyFieldShouldThrow() {
        CIEValidationData cieValidationData = new CIEValidationData();
        NISData nisData = new NISData();
        nisData.setNis("");
        nisData.setSod("c29k");
        nisData.setPubKey("cHVia2V5");
        MRTDData mrtdData = new MRTDData();
        mrtdData.setSod("bXJ0ZFNvZA==");
        mrtdData.setDg1("ZGcx");
        mrtdData.setDg11("ZGcxMQ==");
        cieValidationData.setNisData(nisData);
        cieValidationData.setMrtdData(mrtdData);
        cieValidationData.setSignedNonce("c2lnbmVkTm9uY2U=");

        PnMandateBadRequestException ex = assertThrows(PnMandateBadRequestException.class, () -> validator.validateCieValidationData(cieValidationData));
        checkExceptionDetails(ex, "Missing or empty field: NIS");
    }

}