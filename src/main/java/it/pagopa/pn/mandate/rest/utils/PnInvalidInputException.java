package it.pagopa.pn.mandate.rest.utils;

import it.pagopa.pn.common.rest.error.v1.dto.ProblemError;
import it.pagopa.pn.commons.exceptions.PnValidationException;

import java.util.List;

import static it.pagopa.pn.mandate.rest.utils.PnMandateExceptionCodes.ERROR_CODE_INVALID_PARAMETER;

public class PnInvalidInputException extends PnValidationException {

    public PnInvalidInputException(String field) {
        super("Parametro non valido", List.of(ProblemError.builder()
                .code(ERROR_CODE_INVALID_PARAMETER)
                .element(field)
                .build()) );
    }

}
