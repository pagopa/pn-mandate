package it.pagopa.pn.ciechecker.model;

public enum ResultCieChecker {
    OK("OK"),
    KO("KO"),
    KO_INPUT_PARAMETER_NULL("One or more input parameters are null"),
    KO_CSCA_ANCHORS_NULL("CSCA anchors are null"),
    KO_NOTFOUND_CERT("No certificates found in PKCS7"),
    KO_NOTFOUND_SIGNATURES_SIGNED_DATA("No signatures found in Signed Data");

    private String value;
    ResultCieChecker(String value) {
        this.value = value;
    }
}
