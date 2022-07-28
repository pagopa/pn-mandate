package it.pagopa.pn.mandate.rest.utils;

import it.pagopa.pn.commons.exceptions.ExceptionHelper;

public class PnMandateExceptionCodes {

    // raccolgo qui tutti i codici di errore delle deleghe
    public static final String ERROR_CODE_MANDATE_NOT_FOUND = "PN_MANDATE_NOT_FOUND";
    public static final String ERROR_CODE_MANDATE_ALREADY_EXISTS = "PN_MANDATE_ALREADY_EXISTS";
    public static final String ERROR_CODE_MANDATE_DELEGATE_HIMSELF = "PN_MANDATE_DELEGATE_HIMSELF";
    public static final String ERROR_CODE_INVALID_VERIFICATION_CODE = "PN_MANDATE_INVALID_VERIFICATION_CODE";
    public static final String ERROR_CODE_INVALID_PARAMETER= ExceptionHelper.ERROR_CODE_INVALID_PARAMETER;
}
