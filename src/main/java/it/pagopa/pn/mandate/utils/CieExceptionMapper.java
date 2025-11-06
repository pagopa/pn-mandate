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
            map.put(error.getResult(),
                    () -> new PnInvalidCieDataException(
                            error.getType().getMessage(),
                            error.getType().getCode())
            );
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


    @Getter
    private enum ResultCieClientError {
        // Invalid input errors
        KO_EXC_INVALID_CMSTYPEDDATA(ResultCieChecker.KO_EXC_INVALID_CMSTYPEDDATA, ResultCieClientErrorType.CIE_INVALID_INPUT),
        KO_EXC_PARSING_HEX_BYTE(ResultCieChecker.KO_EXC_PARSING_HEX_BYTE, ResultCieClientErrorType.CIE_INVALID_INPUT),
        KO_EXC_NOTFOUND_MRTD_SOD(ResultCieChecker.KO_EXC_NOTFOUND_MRTD_SOD, ResultCieClientErrorType.CIE_INVALID_INPUT),
        KO_EXC_GENERATE_CMSSIGNEDDATA(ResultCieChecker.KO_EXC_GENERATE_CMSSIGNEDDATA, ResultCieClientErrorType.CIE_INVALID_INPUT),
        KO_EXC_NOTFOUND_CERTIFICATES(ResultCieChecker.KO_EXC_NOTFOUND_CERTIFICATES, ResultCieClientErrorType.CIE_INVALID_INPUT),
        KO_EXC_INVALID_PARAMETER_CODFISCDELEGANTE(ResultCieChecker.KO_EXC_INVALID_PARAMETER_CODFISCDELEGANTE, ResultCieClientErrorType.CIE_INVALID_INPUT),
        // Integrity errors
        KO_EXC_NOT_SAME_DIGEST(ResultCieChecker.KO_EXC_NOT_SAME_DIGEST, ResultCieClientErrorType.CIE_INTEGRITY_ERROR),
        KO_EXC_NO_MATCH_NIS_HASHES_DATAGROUP(ResultCieChecker.KO_EXC_NO_MATCH_NIS_HASHES_DATAGROUP, ResultCieClientErrorType.CIE_INTEGRITY_ERROR),
        KO_EXC_NO_HASH_CONTENT_MATCH(ResultCieChecker.KO_EXC_NO_HASH_CONTENT_MATCH, ResultCieClientErrorType.CIE_INTEGRITY_ERROR),
        // Signature errors
        KO_EXC_CERTIFICATE_NOT_SIGNED(ResultCieChecker.KO_EXC_CERTIFICATE_NOT_SIGNED, ResultCieClientErrorType.CIE_SIGNATURE_ERROR),
        KO_EXC_GENERATE_PUBLICKEY(ResultCieChecker.KO_EXC_GENERATE_PUBLICKEY, ResultCieClientErrorType.CIE_SIGNATURE_ERROR),
        KO_EXC_EXTRACTION_PUBLICKEY(ResultCieChecker.KO_EXC_EXTRACTION_PUBLICKEY, ResultCieClientErrorType.CIE_SIGNATURE_ERROR),
        KO_EXC_NOVALID_DIGITAL_SIGNATURE(ResultCieChecker.KO_EXC_NOVALID_DIGITAL_SIGNATURE, ResultCieClientErrorType.CIE_SIGNATURE_ERROR),
        KO_EXC_NOFOUND_CODFISCALE_DG11(ResultCieChecker.KO_EXC_NOFOUND_CODFISCALE_DG11, ResultCieClientErrorType.CIE_SIGNATURE_ERROR),

        // CIE not related to delegator
        KO_EXC_CODFISCALE_NOT_VERIFIED(ResultCieChecker.KO_EXC_CODFISCALE_NOT_VERIFIED, ResultCieClientErrorType.CIE_NOT_RELATED_TO_DELEGATOR_ERROR),
        // CIE Expired
        KO_EXC_EXPIRATIONDATE(ResultCieChecker.KO_EXC_EXPIRATIONDATE, ResultCieClientErrorType.CIE_EXPIRED_ERROR);


        private final ResultCieClientErrorType type;
        private final ResultCieChecker result;

        ResultCieClientError(ResultCieChecker resultCieChecker, ResultCieClientErrorType type) {
            this.type = type;
            this.result = resultCieChecker;
        }
    }


    @Getter
    private enum ResultCieClientErrorType {
        CIE_INVALID_INPUT("Invalid input data provided"), // Missing or malformed input
        CIE_INTEGRITY_ERROR("Data integrity check failed"), // Data integrity issues
        CIE_SIGNATURE_ERROR("Signature verification failed"), // Signature verification issues
        CIE_NOT_RELATED_TO_DELEGATOR_ERROR("CIE is valid, but not related to the delegator of given mandate"),
        CIE_EXPIRED_ERROR("CIE has expired"); // CIE expired

        private final String message;
        private final String code;

        ResultCieClientErrorType(String message) {
            this.message = message;
            this.code = this.name();
        }
    }
}
