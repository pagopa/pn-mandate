package it.pagopa.pn.mandate.utils;

import it.pagopa.pn.ciechecker.model.ResultCieChecker;
import it.pagopa.pn.commons.exceptions.PnInternalException;
import it.pagopa.pn.commons.exceptions.PnRuntimeException;
import it.pagopa.pn.mandate.exceptions.PnInvalidCieDataException;
import it.pagopa.pn.mandate.exceptions.PnInvalidVerificationCodeException;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

@Component
public class CieExceptionMapper {
    private static final String CIE_CHECKER_SERVER_ERROR_CODE = "CIE_CHECKER_SERVER_ERROR";
    private final Map<ResultCieChecker, Supplier<? extends PnRuntimeException>> exceptionMap;

    public CieExceptionMapper() {
        this.exceptionMap = buildExceptionMap();
    }

    private Map<ResultCieChecker, Supplier<? extends PnRuntimeException>> buildExceptionMap() {
        Map<ResultCieChecker, Supplier<? extends PnRuntimeException>> map =
                new EnumMap<>(ResultCieChecker.class);

        // Nonce error
        map.put(ResultCieChecker.KO_EXC_NO_MATCH_NONCE_SIGNATURE,
                () -> new PnInvalidVerificationCodeException("Nonce signature verification failed"));

        // Client errors
        for (ResultCieClientError error : ResultCieClientError.values()) {
            map.put(error.getResult(), () -> new PnInvalidCieDataException(error.getType()));
        }

        return Collections.unmodifiableMap(map);
    }

    public PnRuntimeException mapToException(ResultCieChecker result) {
        Supplier<? extends PnRuntimeException> exceptionSupplier = exceptionMap.get(result);

        if (exceptionSupplier != null) {
            return exceptionSupplier.get();
        }

        String errorMessage = String.format("CIE Checker server error for code: %s", result.name());
        return new PnInternalException(errorMessage, CIE_CHECKER_SERVER_ERROR_CODE);
    }
}
