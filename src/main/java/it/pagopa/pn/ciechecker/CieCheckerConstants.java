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

}
