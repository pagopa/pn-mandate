package it.pagopa.pn.mandate.exceptions;

import it.pagopa.pn.commons.exceptions.PnRuntimeException;
import it.pagopa.pn.mandate.utils.CieExceptionMapper;
import it.pagopa.pn.mandate.utils.ResultCieClientErrorType;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class PnInvalidCieDataException extends PnRuntimeException {
    private final ResultCieClientErrorType errorType;

    public PnInvalidCieDataException(ResultCieClientErrorType errorType) {
        super(
            "CIE Data validation error",
            "CIE Data Validation Failed: Client-side issue encountered",
            HttpStatus.UNPROCESSABLE_ENTITY.value(),
            errorType.name(),
            null,
            errorType.getMessage()
        );
        this.errorType = errorType;
    }
}
