package it.pagopa.pn.mandate.exceptions;

import it.pagopa.pn.commons.exceptions.PnRuntimeException;
import it.pagopa.pn.commons.exceptions.dto.ProblemError;
import org.springframework.http.HttpStatus;

import java.util.List;

public class PnUnsupportedFilterException extends PnRuntimeException {

    public PnUnsupportedFilterException(String errorCode, String field) {
        super(HttpStatus.BAD_REQUEST.getReasonPhrase(), "Filtro non valido", HttpStatus.BAD_REQUEST.value(), List.of(ProblemError.builder()
                .code(errorCode)
                .element(field)
                .build()), null );
    }

}
