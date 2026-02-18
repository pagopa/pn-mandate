package it.pagopa.pn.mandate.utils;

import it.pagopa.pn.mandate.exceptions.PnInvalidCieDataException;
import it.pagopa.pn.mandate.exceptions.PnInvalidVerificationCodeException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ValidationErrorCategoryTest {
    @Test
    void fromThrowableReturnsFR01ForPnInvalidVerificationCodeException() {
        Throwable ex = new PnInvalidVerificationCodeException("Invalid code");
        assertEquals(ValidationErrorCategory.FR01, ValidationErrorCategory.fromThrowable(ex));
    }

    @Test
    void fromThrowableReturnsFR02ForCieExpiredError() {
        PnInvalidCieDataException ex = new PnInvalidCieDataException(ResultCieClientErrorType.CIE_EXPIRED_ERROR);
        assertEquals(ValidationErrorCategory.FR02, ValidationErrorCategory.fromThrowable(ex));
    }

    @Test
    void fromThrowableReturnsFR03ForCieNotRelatedToDelegatorError() {
        PnInvalidCieDataException ex = new PnInvalidCieDataException(ResultCieClientErrorType.CIE_NOT_RELATED_TO_DELEGATOR_ERROR);
        assertEquals(ValidationErrorCategory.FR03, ValidationErrorCategory.fromThrowable(ex));
    }

    @Test
    void fromThrowableReturnsFR04ForCieIntegrityError() {
        PnInvalidCieDataException ex =  new PnInvalidCieDataException(ResultCieClientErrorType.CIE_INTEGRITY_ERROR);
        assertEquals(ValidationErrorCategory.FR04, ValidationErrorCategory.fromThrowable(ex));
    }

    @Test
    void fromThrowableReturnsTECHForUnknownException() {
        Throwable ex = new RuntimeException("Unknown error");
        assertEquals(ValidationErrorCategory.TECH, ValidationErrorCategory.fromThrowable(ex));
    }

    @Test
    void fromThrowableReturnsTECHForPnInvalidCieDataExceptionWithUnhandledErrorType() {
        PnInvalidCieDataException ex = new PnInvalidCieDataException(ResultCieClientErrorType.CIE_INVALID_INPUT);
        assertEquals(ValidationErrorCategory.TECH, ValidationErrorCategory.fromThrowable(ex));
    }

    @Test
    void enumValueMatchesName() {
        for (ValidationErrorCategory category : ValidationErrorCategory.values()) {
            assertEquals(category.name(), category.getValue());
        }
    }
}