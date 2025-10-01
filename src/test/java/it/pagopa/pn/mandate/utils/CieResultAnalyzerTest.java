package it.pagopa.pn.mandate.utils;

import it.pagopa.pn.ciechecker.model.ResultCieChecker;
import it.pagopa.pn.commons.exceptions.PnInternalException;
import it.pagopa.pn.commons.exceptions.PnRuntimeException;
import it.pagopa.pn.mandate.exceptions.PnInvalidCieDataException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CieResultAnalyzerTest {
    private final CieResultAnalyzer analyzer = new CieResultAnalyzer();

    private void verifyCode(PnRuntimeException ex, String cieCheckerServerError) {
        assertEquals(cieCheckerServerError, ex.getProblem().getErrors().get(0).getCode());
    }

    @Test
    @DisplayName("Should return without exception when result is OK")
    void analyzeResultWithOkShouldReturn() {
        assertDoesNotThrow(() -> analyzer.analyzeResult(ResultCieChecker.OK));
    }

    @Test
    @DisplayName("Should throw PnInternalException when result is null")
    void analyzeResultWithNullShouldThrowInternalException() {
        PnInternalException ex = assertThrows(PnInternalException.class, () -> analyzer.analyzeResult(null));
        verifyCode(ex, "CIE_CHECKER_NULL_RESULT");
    }

    @Test
    @DisplayName("Should throw PnInvalidCieDataException for client error result")
    void analyzeResultWithClientErrorShouldThrowInvalidCieDataException() {
        PnInvalidCieDataException ex = assertThrows(PnInvalidCieDataException.class, () ->
                analyzer.analyzeResult(ResultCieChecker.KO_EXC_INVALID_CMSTYPEDDATA));
        verifyCode(ex, "CIE_INVALID_INPUT");
    }

    @Test
    @DisplayName("Should throw PnInternalException for unmapped error result")
    void analyzeResultWithUnmappedErrorShouldThrowInternalException() {
        PnInternalException ex = assertThrows(PnInternalException.class, () ->
                analyzer.analyzeResult(ResultCieChecker.KO_EXC_INVALID_VERIFIER));
        verifyCode(ex, "CIE_CHECKER_SERVER_ERROR");
    }



    @Test
    @DisplayName("Should throw PnInvalidCieDataException for integrity error result")
    void analyzeResultWithIntegrityErrorShouldThrowInvalidCieDataException() {
        PnInvalidCieDataException ex = assertThrows(PnInvalidCieDataException.class, () ->
                analyzer.analyzeResult(ResultCieChecker.KO_EXC_NOT_SAME_DIGEST));
        verifyCode(ex, "CIE_INTEGRITY_ERROR");
    }

    @Test
    @DisplayName("Should throw PnInvalidCieDataException for signature error result")
    void analyzeResultWithSignatureErrorShouldThrowInvalidCieDataException() {
        PnInvalidCieDataException ex = assertThrows(PnInvalidCieDataException.class, () ->
                analyzer.analyzeResult(ResultCieChecker.KO_EXC_CERTIFICATE_NOT_SIGNED));
        verifyCode(ex, "CIE_SIGNATURE_ERROR");
    }
}