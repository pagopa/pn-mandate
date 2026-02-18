package it.pagopa.pn.mandate.utils;

import it.pagopa.pn.ciechecker.model.ResultCieChecker;
import lombok.Getter;

@Getter
public enum ResultCieClientError {
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