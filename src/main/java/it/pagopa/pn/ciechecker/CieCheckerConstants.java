package it.pagopa.pn.ciechecker;

public class CieCheckerConstants {

    private CieCheckerConstants() {}

    // constants
    public static final String X_509 = "X.509";
    public static final String PKIX = "PKIX";
    public static final String BOUNCY_CASTLE_PROVIDER = "BC";
    public static final String SHA_1 = "SHA-1";
    public static final String SHA_256 = "SHA-256";
    public static final String SHA_384 = "SHA-384";
    public static final String SHA_512 = "SHA-512";

    // exception messages
    public static final String EXC_NO_CERT="No certificates found in PKCS7";
    public static final String PARSED_ZERO_CSCA_CERTIFICATES="Parsed 0 CSCA certificates";
    public static final String NO_CSCA_ANCHORS_PROVIDED= "No CSCA anchors provided";

    public static final String EXC_INPUT_PARAMETER_NULL="Input parameter is null";
    public static final String EXC_NO_SIGNATURES_SIGNED_DATA="No signatures found in Signed Data";
    public static final String EXC_NO_HASH_CONTENT_MATCH="No HashContent match";
    public static final String EXC_NO_HASH_SIGNED_DATA = "No hash of Signed Data found";
    public static final String EXC_NO_SIGNED_ATTRIBUTE = "No signed Attributes found";
    public static final String EXC_NO_MATCH_NIS_HASHES_DATAGROUP ="The NIS hashes DataGroup do not match with the expected value";
    public static final String EXC_NO_CERTIFICATE_NOT_SIGNED = "the certificate has not been signed";
    public static final String EXC_NO_HASH_ALGORITHM_SOD = "Unable to determine hash algorithm from SOD";
    public static final String EXC_UNSUPPORTED_HASH_ALGORITHM_SOD = "Unsupported hash algorithm for SOD";
    public static final String EXC_NO_EXPECTED_HASHES_FOUND_SOD = "No expected hashes found in SOD";
    public static final String EXC_NOTFOUND_DIGEST = "One ore more digest not found.";
    public static final String EXC_NOTFOUND_MRTD_SOD = "Mrtd SOD is empty: must be present";
}
