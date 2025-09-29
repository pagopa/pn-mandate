package it.pagopa.pn.mandate.utils;

import it.pagopa.pn.ciechecker.model.ResultCieChecker;
import it.pagopa.pn.commons.exceptions.PnInternalException;
import it.pagopa.pn.mandate.exceptions.PnInvalidCieDataException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@Slf4j
public class CieResultAnalyzer {

    private static final Map<ResultCieChecker, ResultCieClientError> CLIENT_ERROR_MAP =
            Arrays.stream(ResultCieClientError.values())
                    .collect(Collectors.toMap(
                            ResultCieClientError::getResult,
                            error -> error
                    ));

    /**
     * Analyze the result of the CIE Checker and throw appropriate exceptions based on the result.
     * If the result is OK, the method simply returns.
     * @param checkerResult the result from the CIE Checker to analyze
     * @throws PnInvalidCieDataException Se l'errore è causato da dati non validi forniti dal client (422).
     * @throws PnInternalException Se l'errore è dovuto a problemi di sistema del checker (500).
     */
    public void analyzeResult(ResultCieChecker checkerResult) {
        log.info("Analyzing CIE Checker result: {}", checkerResult);
        if (checkerResult == null) {
            log.error("CIE Checker returned a null result");
            throw new PnInternalException("CIE Checker returned a null result", "CIE_CHECKER_NULL_RESULT");
        }
        if (checkerResult == ResultCieChecker.OK) {
            log.debug("CIE Checker validation successful (OK).");
            return;
        }
        ResultCieClientError clientError = CLIENT_ERROR_MAP.get(checkerResult);

        if (clientError != null) {
            log.warn("CIE Checker client data error identified: {} -> Message: '{}'",
                    checkerResult.name(), clientError.getType().getMessage());
            throw new PnInvalidCieDataException(clientError.getType().getMessage(), clientError.getType().getCode());
        } else {
            log.error("CIE Checker server/unmapped error. Result code: {}", checkerResult.name());
            throw new PnInternalException("CIE Checker server error for code: " + checkerResult.name(),
                    "CIE_CHECKER_SERVER_ERROR");
        }
    }


    @Getter
    private enum ResultCieClientError {
        // Invalid input errors
        KO_EXC_INVALID_CMSTYPEDDATA(ResultCieChecker.KO_EXC_INVALID_CMSTYPEDDATA, ResultCieClientErrorType.CIE_INVALID_INPUT),
        KO_EXC_PARSING_HEX_BYTE(ResultCieChecker.KO_EXC_PARSING_HEX_BYTE, ResultCieClientErrorType.CIE_INVALID_INPUT),
        KO_EXC_NOTFOUND_MRTD_SOD(ResultCieChecker.KO_EXC_NOTFOUND_MRTD_SOD, ResultCieClientErrorType.CIE_INVALID_INPUT),
        KO_EXC_GENERATE_CMSSIGNEDDATA(ResultCieChecker.KO_EXC_GENERATE_CMSSIGNEDDATA, ResultCieClientErrorType.CIE_INVALID_INPUT),
        KO_EXC_NOTFOUND_CERTIFICATES(ResultCieChecker.KO_EXC_NOTFOUND_CERTIFICATES, ResultCieClientErrorType.CIE_INVALID_INPUT),
        KO_EXC_NOT_SAME_DIGEST(ResultCieChecker.KO_EXC_NOT_SAME_DIGEST, ResultCieClientErrorType.CIE_INTEGRITY_ERROR),
        KO_EXC_NO_MATCH_NIS_HASHES_DATAGROUP(ResultCieChecker.KO_EXC_NO_MATCH_NIS_HASHES_DATAGROUP, ResultCieClientErrorType.CIE_INTEGRITY_ERROR),
        KO_EXC_NO_HASH_CONTENT_MATCH(ResultCieChecker.KO_EXC_NO_HASH_CONTENT_MATCH, ResultCieClientErrorType.CIE_INTEGRITY_ERROR),
        KO_EXC_CERTIFICATE_NOT_SIGNED(ResultCieChecker.KO_EXC_CERTIFICATE_NOT_SIGNED, ResultCieClientErrorType.CIE_SIGNATURE_ERROR),
        KO_EXC_NO_MATCH_NONCE_SIGNATURE(ResultCieChecker.KO_EXC_NO_MATCH_NONCE_SIGNATURE, ResultCieClientErrorType.CIE_SIGNATURE_ERROR),
        KO_EXC_GENERATE_PUBLICKEY(ResultCieChecker.KO_EXC_GENERATE_PUBLICKEY, ResultCieClientErrorType.CIE_SIGNATURE_ERROR),
        KO_EXC_EXTRACTION_PUBLICKEY(ResultCieChecker.KO_EXC_EXTRACTION_PUBLICKEY, ResultCieClientErrorType.CIE_SIGNATURE_ERROR);

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
        CIE_SIGNATURE_ERROR("Signature verification failed"); // Signature verification issues

        private final String message;
        private final String code;

        ResultCieClientErrorType(String message) {
            this.message = message;
            this.code = this.name().toLowerCase();
        }
    }
}