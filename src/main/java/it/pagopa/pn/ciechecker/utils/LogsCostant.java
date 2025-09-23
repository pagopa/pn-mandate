package it.pagopa.pn.ciechecker.utils;


import it.pagopa.pn.ciechecker.CieChecker;

public abstract class LogsCostant {

    // METODO API
    public static final String CIECHECKER_VALIDATE_MANDATE = "CieCheckerImpl.validateMandate()";

    public static final String CIECHECKER_INIT = "CieCheckerImpl.init()";

    // METODI
    public static final String CIECHECKER_VALIDATE_DATA_INPUT = "CieCheckerImpl.validateDataInput()";
    public static final String CIECHECKER_VERIFY_DIGITAL_SIGNATURE = "CieCheckerImpl.verifyDigitalSignature()";
    public static final String CIECHECKER_VERIFY_TRUST_CHAIN = "CieCheckerImpl.verifyTrustChain()";
    public static final String CIECHECKER_VERIFY_SOD_PASSIVE_AUTH_CIE = "CieCheckerImpl.verifySodPassiveAuthCie()";
    public static final String CIECHECKER_VERIFY_CHALLENGE_FROM_SIGNATURE = "CieCheckerImpl.verifyChallengeFromSignature()";
    public static final String CIECHECKER_VERIFY_INTEGRITY = "CieCheckerImpl.verifyIntegrity()";
    public static final String CIECHECKER_VERIFY_INTEGRITY_CORE = "CieCheckerImpl.verifyIntegrityCore()";
    public static final String CIECHECKER_VERIFY_DIGESTLIST = "CieCheckerImpl.verifyDigestList()";
    public static final String VALIDATEUTILS_VERIFY_DSC_AGAINST_TRUST_BUNDLE = "ValidateUtils.verifyDscAgainstTrustBundle()";
    public static final String VALIDATEUTILS_EXTRACT_DSC_CERT_DER = "ValidateUtils.extractDscCertDer()";
    public static final String VALIDATEUTILS_EXTRACT_PUBLICKEY_FROM_HOLDER = "ValidateUtils.extractPublicKeyFromHolder()";
    public static final String VALIDATEUTILS_VERIFY_MATCH_HASHCONTENT = "ValidateUtils.verifyMatchHashContent()";
    public static final String VALIDATEUTILS_VERIFY_OCTECTSTRINGS = "ValidateUtils.verifyOctetStrings()";
    public static final String VALIDATEUTILS_CALCULATE_SHA256 = "ValidateUtils.calculateSha256()";
    public static final String VALIDATEUTILS_EXTRACT_HASHSIGNED = "ValidateUtils.extractHashSigned()";
    public static final String VALIDATEUTILS_EXTRACT_ALLSIGNEDATTR = "ValidateUtils.extractAllSignedAttributes()";
    public static final String VALIDATEUTILS_EXTRACT_DATAGROUP = "ValidateUtils.extractDataGroupHashes()";
    public static final String VALIDATEUTILS_EXTRACT_CSCAANCHOR = "ValidateUtils.extractCscaAnchorFromZip()";
    public static final String VALIDATEUTILS_GETX509CERTLIST_ZIPFILE = "ValidateUtils.getX509CertListFromZipFile()";
    public static final String VALIDATEUTILS_VERIFY_DIGITAL_SIGNATURE = "ValidateUtils.verifyDigitalSignature()";


    // ERROR
    public static final String ENDING_PROCESS_WITH_ERROR = "Ending '{}' Process with error = '{}' - '{}'";
    public static final String INVALID_REQUEST = "Invalid Request";

    public static final String SUCCESSFUL_OPERATION_ON_LABEL = "Successful operation on '{}' : '{}' = {}";
    public static final String SUCCESSFUL_OPERATION_NO_RESULT_LABEL = "Successful operation: '{}'";
    public static final String EXCEPTION_IN_PROCESS = "Exception in '{}' - ResultCieChecker: {}";

    public static final String INVOKING_OPERATION_LABEL_WITH_ARGS = "Invoking operation '{}' with args: {}";
    public static final String INVOKING_OPERATION_LABEL = "Invoking operation '{}'";

    private LogsCostant() {
        throw new IllegalStateException("LogsCostant is a utility class");
    }
}
