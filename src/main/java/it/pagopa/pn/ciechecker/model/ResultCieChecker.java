package it.pagopa.pn.ciechecker.model;

import it.pagopa.pn.ciechecker.CieCheckerConstants;

public enum ResultCieChecker {
    OK("OK"),
    KO("KO"),
    KO_INPUT_PARAMETER_NULL( CieCheckerConstants.EXC_INPUT_PARAMETER_NULL), //"One or more input parameters are null"),
    KO_NOTFOUND_CERT(CieCheckerConstants.EXC_NO_CERT),
    KO_NOTFOUND_SIGNATURES_SIGNED_DATA(CieCheckerConstants.EXC_NO_SIGNATURES_SIGNED_DATA),
    KO_EXC_CERTIFICATE_NOT_SIGNED (CieCheckerConstants.EXC_CERTIFICATE_NOT_SIGNED),
    KO_NO_CSCA_ANCHORS_PROVIDED ( CieCheckerConstants.NO_CSCA_ANCHORS_PROVIDED),
    KO_PARSED_ZERO_CSCA_CERTIFICATES (CieCheckerConstants.PARSED_ZERO_CSCA_CERTIFICATES),
    KO_NO_SIGNERINFORMATION(CieCheckerConstants.EXC_NO_SIGNERINFORMATION),
    KO_PARSING_CERTIFICATION(CieCheckerConstants.EXC_PARSING_CERTIFICATION),
    KO_EXC_GENERATE_CERTIFICATE(CieCheckerConstants.EXC_GENERATE_CERTIFICATE),
    KO_EXC_GENERATE_CMSSIGNEDDATA(CieCheckerConstants.EXC_GENERATE_CMSSIGNEDDATA),
    KO_EXC_NO_SUPPORTED_CERTIFICATEFACTORY(CieCheckerConstants.EXC_NO_SUPPORTED_CERTIFICATEFACTORY),
    KO_EXC_VALIDATE_CERTIFICATE(CieCheckerConstants.EXC_VALIDATE_CERTIFICATE),
    KO_EXC_NO_SUPPORTED_CERTIFICATEPATHVALIDATOR(CieCheckerConstants.EXC_NO_SUPPORTED_CERTIFICATEPATHVALIDATOR),
    KO_EXC_INVALID_PARAMETER_CERTPATHVALIDATOR(CieCheckerConstants.EXC_INVALID_PARAMETER_CERTPATHVALIDATOR),
    KO_EXC_IOEXCEPTION(CieCheckerConstants.EXC_IOEXCEPTION),
    KO_NOT_SAME_DIGEST("Digest mismatch between expected and actual DG"),
    KO_DIGEST_NOT_VERIFIED("Digest are different, validation of integrity not passed"),
    KO_HASH_ALGORITHM_SOD(CieCheckerConstants.EXC_NO_HASH_ALGORITHM_SOD),
    KO_UNSUPPORTED_ALGORITHM_SOD(CieCheckerConstants.EXC_UNSUPPORTED_HASH_ALGORITHM_SOD),
    KO_NOTFOUND_EXPECTED_HASHES_SOD(CieCheckerConstants.EXC_NO_EXPECTED_HASHES_FOUND_SOD),
    KO_NOTFOUND_DIGEST_SOD(CieCheckerConstants.EXC_NOTFOUND_DIGEST),
    KO_NOTFOUND_MRTD_SOD(CieCheckerConstants.EXC_NOTFOUND_MRTD_SOD),
    KO_NO_MATCH_NONCE_SIGNATURE(CieCheckerConstants.NO_MATCH_NONCE_SIGNATURE),
    KO_EXC_GENERATE_PUBLICKEY(CieCheckerConstants.EXC_GENERATE_PUBLICKEY),
    KO_EXC_PARSING_HEX_BYTE(CieCheckerConstants.EXC_PARSING_HEX_BYTE),
    KO_EXC_INVALID_CRYPTOGRAPHIC_OPERATION(CieCheckerConstants.EXC_INVALID_CRYPTOGRAPHIC_OPERATION),
    KO_EXC_NOT_AVAILABLE_CRYPTOGRAPHIC_ALGORITHM(CieCheckerConstants.EXC_NOT_AVAILABLE_CRYPTOGRAPHIC_ALGORITHM),
    KO_EXC_NOT_AVAILABLE_SECURITY_PROVIDER(CieCheckerConstants.EXC_NOT_AVAILABLE_SECURITY_PROVIDER),
    KO_EXC_INVALID_KEY_SPECIFICATION(CieCheckerConstants.EXC_INVALID_KEY_SPECIFICATION),
    KO_EXC_NOFOUND_DIGITAL_SIGNATURE(CieCheckerConstants.EXC_NOFOUND_DIGITAL_SIGNATURE),
    KO_EXC_NOFOUND_SIGNER(CieCheckerConstants.EXC_NOFOUND_SIGNER);

    private String value;
    public String getValue() {
        return value;
    }

    ResultCieChecker(String value) {
        this.value = value;
    }

    public static ResultCieChecker fromValue(String value) {
        for (ResultCieChecker b : ResultCieChecker.values()) {
            if (b.value.equals(value)) {
                return b;
            }
        }
        return ResultCieChecker.KO;
    }
}
