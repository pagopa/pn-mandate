package it.pagopa.pn.mandate.rest.utils;

import it.pagopa.pn.commons.exceptions.PnRuntimeException;
import org.springframework.http.HttpStatus;

import static it.pagopa.pn.mandate.rest.utils.PnMandateExceptionCodes.ERROR_CODE_MANDATE_ALREADY_EXISTS;

public class PnMandateByHimselfException extends PnRuntimeException {

    public PnMandateByHimselfException() {
        super("Delega già presente", "Non è possibile delegare se stessi", HttpStatus.CONFLICT.value(), ERROR_CODE_MANDATE_ALREADY_EXISTS, null, null);
    }

}
