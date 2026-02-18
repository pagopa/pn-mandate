package it.pagopa.pn.mandate.utils;

import it.pagopa.pn.mandate.exceptions.PnInvalidCieDataException;
import it.pagopa.pn.mandate.exceptions.PnInvalidVerificationCodeException;
import lombok.Getter;

@Getter
public enum ValidationErrorCategory {
    FR01, // Anti-cloning
    FR02, // Expired-CIE
    FR03, // CIE not related to the delegator
    FR04, // Data integrity
    TECH; // Technical error

    private final String value;

    ValidationErrorCategory() {
        	this.value = this.name();
    }

    public static ValidationErrorCategory fromThrowable(Throwable throwable) {
        if(throwable instanceof PnInvalidVerificationCodeException) {
            return FR01;
        }

        if(throwable instanceof PnInvalidCieDataException ex) {
            switch (ex.getErrorType()) {
                case CIE_EXPIRED_ERROR:
                    return FR02;
                case CIE_NOT_RELATED_TO_DELEGATOR_ERROR:
                    return FR03;
                case CIE_INTEGRITY_ERROR:
                    return FR04;
            }
        }

        return TECH; // Default category for unclassified errors
    }
}
