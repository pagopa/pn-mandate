package it.pagopa.pn.ciechecker;

public class CieCheckerConstants {

    private CieCheckerConstants() {}

    // constants
    public static final String X_509 = "X.509";
    public static final String PKIX = "PKIX";
    //public static final String BOUNCY_CASTLE_PROVIDER = "BC";
    public static final String SHA_1 = "SHA-1";
    public static final String SHA_256 = "SHA-256";
    public static final String SHA_384 = "SHA-384";
    public static final String SHA_512 = "SHA-512";

    public static final String OK = "OK";

    // exception messages
    public static final String EXC_NOTFOUND_CERTIFICATES="No certificates found in PKCS7";
    public static final String EXC_PARSED_ZERO_CSCA_CERTIFICATES="Parsed 0 CSCA certificates";
    public static final String EXC_NO_CSCA_ANCHORS_PROVIDED= "No CSCA anchors provided";
    public static final String EXC_INPUT_PARAMETER_NULL="One or more input parameters are null";
    public static final String EXC_NO_SIGNATURES_SIGNED_DATA="No signatures found in Signed Data";
    public static final String EXC_NO_HASH_CONTENT_MATCH="No HashContent match";
    public static final String EXC_NO_HASH_SIGNED_DATA = "No hash of SignedData found";
    public static final String EXC_NO_SIGNED_ATTRIBUTE = "No SignedAttributes found";
    public static final String EXC_NO_MATCH_NIS_HASHES_DATAGROUP = "The NIS hashes DataGroup do not match with the expected value";
    public static final String EXC_CERTIFICATE_NOT_SIGNED = "The certificate has not been signed";
    public static final String EXC_NO_SIGNERINFORMATION = "SignerInformation is null";
    public static final String EXC_PARSING_CERTIFICATION = "Error during the conversion of the certificate to obtain the public key";
    public static final String EXC_GENERATE_CERTIFICATE = "Error in generating the certificate object and initializes";
    public static final String EXC_VALIDATE_CERTIFICATE = "Path does not chain with any of the trust anchors";
    public static final String EXC_GENERATE_CMSSIGNEDDATA = "Error in generating CMSSignedData";
    public static final String EXC_NO_SUPPORTED_CERTIFICATEFACTORY = "No Provider supports a CertificateFactorySpi implementation for the specified type";
    public static final String EXC_NO_SUPPORTED_CERTIFICATEPATHVALIDATOR = "No Provider supports a CertPathValidatorSpi implementation for the specified algorithm";
    public static final String EXC_INVALID_PARAMETER_CERTPATHVALIDATOR = "The specified parameters or the type of the specified CertPath are inappropriate for this CertPathValidator";
    public static final String EXC_IOEXCEPTION = "KO_IOEXCEPTION";
    public static final String EXC_NO_HASH_ALGORITHM_SOD = "Unable to determine hash algorithm from SOD";
    public static final String EXC_UNSUPPORTED_HASH_ALGORITHM_SOD = "Unsupported hash algorithm for SOD";
    public static final String EXC_NO_EXPECTED_HASHES_FOUND_SOD = "No expected hashes found in SOD";
    public static final String EXC_NOTFOUND_DIGEST = "One ore more digest not found.";
    public static final String EXC_NOTFOUND_MRTD_SOD = "Mrtd SOD is empty: must be present";
    public static final String EXC_GENERATE_PUBLICKEY = "Error in generating PublicKey Object";
    public static final String EXC_EXTRACTION_PUBLICKEY = "Error in extraction PublicKey Object";
    public static final String EXC_NO_MATCH_NONCE_SIGNATURE = "The challenge (nonce) from the signature does not match the one extracted from the signature.";
    public static final String EXC_PARSING_HEX_BYTE = "Conversion error from string to byte[]";
    public static final String EXC_INVALID_CRYPTOGRAPHIC_OPERATION = "Unexpected or invalid state occurs during cryptographic operations";
    public static final String EXC_NOT_AVAILABLE_CRYPTOGRAPHIC_ALGORITHM ="The cryptographic algorithm is not available";
    public static final String EXC_NOT_AVAILABLE_SECURITY_PROVIDER ="The security provider is not available";
    public static final String EXC_INVALID_KEY_SPECIFICATION ="Invalid key specifications";
    public static final String EXC_NOFOUND_DIGITAL_SIGNATURE ="No digital signature found in the SignedData structure";
    public static final String EXC_NOFOUND_SIGNER ="No signer found";
    public static final String EXC_NO_CMSTYPEDDATA = "Invalid or unavailable signed content"; //""Contenuto firmato non valido o non disponibile.";
    public static final String EXC_NO_SIGNERINFORMATIONSTORE ="SignerInformationStore is null";
    public static final String EXC_NO_MESSAGEDIGESTSPI_SUPPORTED = "No Provider supports a MessageDigestSpi implementation for the specified algorithm";
    public static final String EXC_INVALID_CMSTYPEDDATA = "The signed content is not a valid hash sequence.";
    public static final String EXC_NO_NIS_HASHES_DATAGROUP ="The NIS hashes DataGroup List is null";
    public static final String EXC_ERROR_CREATE_VERIFIER = "Unable to create verifier";
    public static final String EXC_NOVALID_DIGITAL_SIGNATURE = "No valid digital Signature";
    public static final String EXC_NOT_SAME_DIGEST = "Digest mismatch between expected and actual DG";
    public static final String EXC_DIGEST_NOT_VERIFIED = "Digest are different, validation of integrity not passed";
}
