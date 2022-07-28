package it.pagopa.pn.mandate.rest.utils;

import it.pagopa.pn.commons.exceptions.PnRuntimeException;
import org.springframework.http.HttpStatus;

import static it.pagopa.pn.mandate.rest.utils.PnMandateExceptionCodes.ERROR_CODE_MANDATE_NOT_FOUND;

public class PnMandateNotFoundException extends PnRuntimeException {

    public PnMandateNotFoundException() {
        super("Delega non presente", "Non è stata trovata nessuna delega valida", HttpStatus.NOT_FOUND.value(), ERROR_CODE_MANDATE_NOT_FOUND, null, null);
    }

}
