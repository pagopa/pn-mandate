package it.pagopa.pn.mandate.utils;

import it.pagopa.pn.ciechecker.model.ResultCieChecker;
import it.pagopa.pn.commons.exceptions.PnInternalException;
import it.pagopa.pn.commons.exceptions.PnRuntimeException;
import it.pagopa.pn.mandate.exceptions.PnInvalidCieDataException;
import it.pagopa.pn.mandate.exceptions.PnInvalidVerificationCodeException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static it.pagopa.pn.mandate.exceptions.PnMandateExceptionCodes.ERROR_CODE_INVALID_VERIFICATION_CODE;
import static org.junit.jupiter.api.Assertions.*;

class CieExceptionMapperTest {
    private final CieExceptionMapper mapper = new CieExceptionMapper();

    private void verifyCode(PnRuntimeException ex, String cieCheckerServerError) {
        assertEquals(cieCheckerServerError, ex.getProblem().getErrors().get(0).getCode());
    }

    @Test
    @DisplayName("Should throw PnInvalidCieDataException for client error result")
    void mapToExceptionWithClientErrorShouldThrowInvalidCieDataException() {
        PnRuntimeException ex = mapper.mapToException(ResultCieChecker.KO_EXC_INVALID_CMSTYPEDDATA);
        assertInstanceOf(PnInvalidCieDataException.class, ex);
        verifyCode(ex, "CIE_INVALID_INPUT");
    }

    @Test
    @DisplayName("Should throw PnInvalidCieDataException for integrity error result")
    void mapToExceptionWithIntegrityErrorShouldThrowInvalidCieDataException() {
        PnRuntimeException ex = mapper.mapToException(ResultCieChecker.KO_EXC_NOT_SAME_DIGEST);
        assertInstanceOf(PnInvalidCieDataException.class, ex);
        verifyCode(ex, "CIE_INTEGRITY_ERROR");
    }

    @Test
    @DisplayName("Should throw PnInvalidCieDataException for signature error result")
    void mapToExceptionWithSignatureErrorShouldThrowInvalidCieDataException() {
        PnRuntimeException ex = mapper.mapToException(ResultCieChecker.KO_EXC_CERTIFICATE_NOT_SIGNED);
        assertInstanceOf(PnInvalidCieDataException.class, ex);
        verifyCode(ex, "CIE_SIGNATURE_ERROR");
    }

    @Test
    @DisplayName("Should throw PnInvalidCieDataException for CIE not related to delegator error result")
    void mapToExceptionWithCieNotRelatedToDelegatorShouldThrowInvalidCieDataException() {
        PnRuntimeException ex = mapper.mapToException(ResultCieChecker.KO_EXC_CODFISCALE_NOT_VERIFIED);
        assertInstanceOf(PnInvalidCieDataException.class, ex);
        verifyCode(ex, "CIE_NOT_RELATED_TO_DELEGATOR_ERROR");
    }

    @Test
    @DisplayName("Should throw PnInvalidCieDataException for CIE expired error result")
    void mapToExceptionWithCieExpiredShouldThrowInvalidCieDataException() {
        PnRuntimeException ex = mapper.mapToException(ResultCieChecker.KO_EXC_EXPIRATIONDATE);
        assertInstanceOf(PnInvalidCieDataException.class, ex);
        verifyCode(ex, "CIE_EXPIRED_ERROR");
    }

    @Test
    @DisplayName("Should throw PnInvalidVerificationCodeException for invalid nonce error result")
    void mapToExceptionWithNonceErrorShouldThrowInvalidVerificationCodeException() {
        PnRuntimeException ex = mapper.mapToException(ResultCieChecker.KO_EXC_NO_MATCH_NONCE_SIGNATURE);
        assertInstanceOf(PnInvalidVerificationCodeException.class, ex);
        verifyCode(ex, ERROR_CODE_INVALID_VERIFICATION_CODE);
    }

    @Test
    @DisplayName("Should throw PnInternalException for unmapped error result")
    void mapToExceptionWithUnmappedErrorShouldThrowInternalException() {
        PnRuntimeException ex = mapper.mapToException(ResultCieChecker.KO_EXC_INVALID_VERIFIER);
        assertInstanceOf(PnInternalException.class, ex);
        verifyCode(ex, "CIE_CHECKER_SERVER_ERROR");
    }
}