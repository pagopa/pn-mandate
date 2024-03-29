package it.pagopa.pn.mandate.exceptions;

import it.pagopa.pn.commons.exceptions.PnExceptionsCodes;

public class PnMandateExceptionCodes extends PnExceptionsCodes {

    // raccolgo qui tutti i codici di errore delle deleghe
    public static final String ERROR_CODE_MANDATE_NOT_FOUND = "PN_MANDATE_NOTFOUND";
    public static final String ERROR_CODE_MANDATE_ALREADY_EXISTS = "PN_MANDATE_ALREADYEXISTS";
    public static final String ERROR_CODE_MANDATE_NOTACCEPTABLE = "PN_MANDATE_NOTACCEPTABLE";
    public static final String ERROR_CODE_MANDATE_NOTACCEPTABLEEXPIRED = "PN_MANDATE_NOTACCEPTABLEEXPIRED";
    public static final String ERROR_CODE_MANDATE_NOTUPDATABLE = "PN_MANDATE_NOTUPDATABLE";
    public static final String ERROR_CODE_MANDATE_DELEGATE_HIMSELF = "PN_MANDATE_DELEGATEHIMSELF";
    public static final String ERROR_CODE_INVALID_VERIFICATION_CODE = "PN_MANDATE_INVALIDVERIFICATIONCODE";
    public static final String ERROR_CODE_INVALID_GROUP = "PN_MANDATE_INVALIDGROUP";
    public static final String ERROR_CODE_MANDATE_INVALID_MESSAGE_HEADERS = "ERROR_CODE_MANDATE_INVALIDMESSAGEHEADERS";
    public static final String ERROR_CODE_MANDATE_HANDLER_NOT_PRESENT = "ERROR_CODE_MANDATE_HANDLERNOTPRESENT";
    public static final String ERROR_CODE_JSON_PROCESSING_SQS_SERVICE = "PN_MANDATE_JSON_PROCESSING_SQS_SERVICE";
    public static final String ERROR_CODE_MANDATE_UNSUPPORTED_LAST_EVALUTED_KEY = "PN_MANDATE_UNSUPPORTED_LAST_EVALUTED_KEY";

}
