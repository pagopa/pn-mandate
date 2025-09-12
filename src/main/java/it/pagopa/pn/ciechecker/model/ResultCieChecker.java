package it.pagopa.pn.ciechecker.model;

import it.pagopa.pn.ciechecker.CieCheckerConstants;
import lombok.Getter;

@Getter
public enum ResultCieChecker {
    OK("OK"),
    KO("KO"),
    KO_INPUT_PARAMETER_NULL("One or more input parameters are null"),
    KO_CSCA_ANCHORS_NULL("CSCA anchors are null"),
    KO_NOTFOUND_CERT("No certificates found in PKCS7"),
    KO_NOTFOUND_SIGNATURES_SIGNED_DATA("No signatures found in Signed Data"),
    KO_NOT_SAME_DIGEST("Digest mismatch between expected and actual DG"),
    KO_DIGEST_NOT_VERIFIED("Digest are different, validation of integrity not passed"),
    KO_HASH_ALGORITHM_SOD(CieCheckerConstants.EXC_NO_HASH_ALGORITHM_SOD),
    KO_UNSUPPORTED_ALGORITHM_SOD(CieCheckerConstants.EXC_UNSUPPORTED_HASH_ALGORITHM_SOD),
    KO_NOTFOUND_EXPECTED_HASHES_SOD(CieCheckerConstants.EXC_NO_EXPECTED_HASHES_FOUND_SOD),
    KO_NOTFOUND_DIGEST_SOD(CieCheckerConstants.EXC_NOTFOUND_DIGEST),
    KO_NOTFOUND_MRTD_SOD(CieCheckerConstants.EXC_NOTFOUND_MRTD_SOD);



    private String value;

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
