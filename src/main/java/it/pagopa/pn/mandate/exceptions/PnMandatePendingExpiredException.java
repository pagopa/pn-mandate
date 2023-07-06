package it.pagopa.pn.mandate.exceptions;

import it.pagopa.pn.commons.exceptions.PnRuntimeException;
import org.springframework.http.HttpStatus;

import static it.pagopa.pn.mandate.exceptions.PnMandateExceptionCodes.*;

public class PnMandatePendingExpiredException extends PnRuntimeException {

    public PnMandatePendingExpiredException() {
        super("Delega con periodo di accettazione scaduta", "accept a pending mandate after max pending duration is not permitted", HttpStatus.BAD_REQUEST.value(), ERROR_CODE_MANDATE_NOTACCEPTABLEEXPIRED, null, null);
    }

}
