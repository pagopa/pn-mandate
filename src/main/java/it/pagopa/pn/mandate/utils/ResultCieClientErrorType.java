package it.pagopa.pn.mandate.utils;

import lombok.Getter;

@Getter
public enum ResultCieClientErrorType {
    CIE_INVALID_INPUT("Invalid input data provided"), // Missing or malformed input
    CIE_INTEGRITY_ERROR("Data integrity check failed"), // Data integrity issues
    CIE_SIGNATURE_ERROR("Signature verification failed"), // Signature verification issues
    CIE_NOT_RELATED_TO_DELEGATOR_ERROR("CIE is valid, but not related to the delegator of given mandate"),
    CIE_EXPIRED_ERROR("CIE has expired"); // CIE expired

    private final String message;

    ResultCieClientErrorType(String message) {
        this.message = message;
    }
}