package it.pagopa.pn.mandate.exceptions;

import it.pagopa.pn.commons.exceptions.ExceptionHelper;
import it.pagopa.pn.commons.exceptions.PnExceptionsCodes;

public class PnMandateExceptionCodes extends PnExceptionsCodes {

    // raccolgo qui tutti i codici di errore delle deleghe
    public static final String ERROR_CODE_MANDATE_NOT_FOUND = "PN_MANDATE_NOTFOUND";
    public static final String ERROR_CODE_MANDATE_ALREADY_EXISTS = "PN_MANDATE_ALREADYEXISTS";
    public static final String ERROR_CODE_MANDATE_NOTACCEPTABLE = "PN_MANDATE_NOTACCEPTABLE";
    public static final String ERROR_CODE_MANDATE_DELEGATE_HIMSELF = "PN_MANDATE_DELEGATEHIMSELF";
    public static final String ERROR_CODE_INVALID_VERIFICATION_CODE = "PN_MANDATE_INVALIDVERIFICATIONCODE";
}
