package it.pagopa.pn.mandate.exceptions;

import it.pagopa.pn.commons.exceptions.PnRuntimeException;
import org.springframework.http.HttpStatus;

public class PnInvalidCieDataException extends PnRuntimeException {
    public PnInvalidCieDataException(String detail, String code) {
        super("CIE Data", "I dati CIE sono invalidi", HttpStatus.UNPROCESSABLE_ENTITY.value(), code, null, detail);
    }
}
