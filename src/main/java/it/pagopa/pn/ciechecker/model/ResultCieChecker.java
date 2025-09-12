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
    KO_EXC_IOEXCEPTION(CieCheckerConstants.EXC_IOEXCEPTION);

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
