package it.pagopa.pn.mandate.validation;

import it.pagopa.pn.ciechecker.CieChecker;
import it.pagopa.pn.ciechecker.CieCheckerConstants;
import it.pagopa.pn.ciechecker.CieCheckerInterface;
import it.pagopa.pn.ciechecker.MasterListMergeToolUtility;
import it.pagopa.pn.ciechecker.client.s3.S3BucketClient;
import it.pagopa.pn.ciechecker.exception.CieCheckerException;
import it.pagopa.pn.ciechecker.model.*;
import it.pagopa.pn.ciechecker.utils.LogsCostant;
import it.pagopa.pn.ciechecker.utils.ValidateUtils;
import it.pagopa.pn.mandate.config.PnMandateConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.IOUtils;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.crypto.encodings.PKCS1Encoding;
import org.bouncycastle.crypto.engines.RSAEngine;
import org.bouncycastle.crypto.params.RSAKeyParameters;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.io.*;
import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPublicKeySpec;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static it.pagopa.pn.ciechecker.CieCheckerConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;


@SpringBootTest(classes = {it.pagopa.pn.ciechecker.CieCheckerImpl.class, it.pagopa.pn.ciechecker.client.s3.S3BucketClientImpl.class})
@Slf4j
@ActiveProfiles("test")
@EnableConfigurationProperties(PnMandateConfig.class)
class CieCheckerTest {

    @Autowired
    private CieChecker cieChecker;
    @Autowired
    private CieCheckerInterface cieCheckerInterface;
    @MockBean
    private S3BucketClient s3BucketClient;
    @MockBean
    private S3Client clientS3;


    private static final Path basePath= Path.of("src","test","resources");
    private static final Path sodFile = Paths.get("src/test/resources/EF_FABIO.SOD");
    private static final Path dg1Files = Paths.get("src/test/resources/DG1_FABIO.HEX");
    private static final Path dg11Files = Paths.get("src/test/resources/EF.DG11");
    private static final Path dg11FilesHex = Paths.get("src/test/resources/DG11_FABIO.HEX");
    private static final Path dg1FilesCorrupted = Paths.get("src/test/resources/DG1_CORROTTO.HEX");
    private static final Path dg11FilesCorroupted = Paths.get("src/test/resources/DG11_CORROTTO.HEX");
    private static final List<String> compatibleAlgorithms = List.of(SHA_256,SHA_384,SHA_512);
    private static final String SOD_HEX_IAS = "SOD_IAS_FABIO.HEX"; //"SOD_IAS_MARIO.HEX";
    private static final String EF_SOD_HEX = "EF_SOD.HEX";
    private static final Path masterListCSCA = Paths.get("src/test/resources/IT_MasterListCSCA.zip");
    private static final String masterListCSCAZip_S3 = "s3://pn-runtime-environment-variables-eu-south-1-830192246553/pn-mandate/csca-masterlist/IT_MasterListCSCA.zip";

    private static final String fileToAddMasterListZip = "src/test/resources/catest.pem";
    private static final String originalMasterListZip = "src/test/resources/IT_MasterListCSCA.zip";

    static CieValidationData validationData;

    @BeforeEach
    void setUp() throws IOException, DecoderException {

//        // inizio a creare l'inputStream che deve tornare la chiamata s3
        InputStream fileInputStream = new FileInputStream(Path.of(CSCA_ANCHOR_PATH_FILENAME).toFile());
        ResponseInputStream<GetObjectResponse> s3Stream = new ResponseInputStream<>(
                GetObjectResponse.builder().build(),
                AbortableInputStream.create(fileInputStream)
        );
//
        when(s3BucketClient.getObjectContent(anyString()))
               .thenAnswer(invocation -> s3Stream);

        //PEM FILE
//        InputStream fileInputStreamPem = new FileInputStream(Path.of(fileToAddMasterListZip).toFile());
//        ResponseInputStream<GetObjectResponse> s3StreamPem = new ResponseInputStream<>(
//                GetObjectResponse.builder().build(),
//                AbortableInputStream.create(fileInputStreamPem)
//        );
////
//        when(s3BucketClient.getObjectContent(anyString()))
//                .thenAnswer(invocation -> s3StreamPem);

        cieChecker.init();

        byte[] nisPubKey = hexFile(cleanString(basePath.resolve("NIS_PUBKEY_FABIO.HEX"))); //"NIS_PUBKEY_MARIO.HEX")));
        byte[] nisSignature = hexFile(cleanString(basePath.resolve("NIS_SIGNATURE_FABIO.HEX")));
        String nisChallenge = cleanString(basePath.resolve("NIS_CHALLENGE.HEX"));
        byte[] nisHexToCheck = "910718464654".getBytes(); //hexFile(cleanString(basePath.resolve("NIS.HEX")));
        byte[] sodIasByteArray = loadSodBytes(basePath.resolve(SOD_HEX_IAS));

        byte[] sodMrtd = Files.readAllBytes(sodFile);
        byte[] dg1 = hexFile(Files.readString(dg1Files));
        byte[] dg11 = hexFile(Files.readString(dg11FilesHex));
        //byte[] dg11 = Files.readAllBytes(dg11Files);

        validationData = new CieValidationData();
        CieIas cieIas = new CieIas();
        cieIas.setPublicKey(nisPubKey);
        cieIas.setNis(nisHexToCheck);
        cieIas.setSod(sodIasByteArray);

        validationData.setCieIas(cieIas);
        validationData.setSignedNonce(nisSignature);
        validationData.setNonce("02461"); // nisChallenge);
        validationData.setCodFiscDelegante("TTNMRA63S21H501V"); //"RSSDNC42R01H501Y");

        CieMrtd cMrtd = new CieMrtd();
        cMrtd.setSod(sodMrtd);
        cMrtd.setDg1(dg1);
        cMrtd.setDg11(dg11);
        validationData.setCieMrtd(cMrtd);

    }


    @Test
    @Order(1)
    void validateMandateTest() throws IOException {
        log.info("TEST validateMandateTest - INIT... ");

        if(validationData.getCieMrtd().getDg1() == null)
            validationData.getCieMrtd().setDg1(Files.readAllBytes(dg1Files));
        if(validationData.getCieMrtd().getDg11() == null)
            validationData.getCieMrtd().setDg11(Files.readAllBytes(dg11Files));

        ResultCieChecker result = cieChecker.validateMandate(validationData);
        assertEquals(CieCheckerConstants.OK , result.getValue());


        // verifica che getObjectContent sia stato chiamato almeno una volta
        Mockito.verify(s3BucketClient, Mockito.atLeastOnce())
                .getObjectContent(anyString());


        log.info("TEST validateMandateTest - END ");
    }

    @Test
    void verifyCodFiscDeleganteTest () throws CieCheckerException {

        validationData.setCodFiscDelegante("RSSDNC42R01H501Y");
        ResultCieChecker resultKO = cieCheckerInterface.verifyCodFiscDelegante(validationData);
        log.info("Risultato atteso OK -> " + resultKO.getValue());
        assertNotEquals(OK, resultKO.getValue());

        validationData.setCodFiscDelegante("RSSMRA95A58H501Z");
        ResultCieChecker resultOK = cieCheckerInterface.verifyCodFiscDelegante(validationData);
        log.info("Risultato atteso OK -> " + resultOK.getValue());
        assertEquals(OK, resultOK.getValue());
    }

    @Test
    void verifyExpirationCieTest () throws CieCheckerException {

        ResultCieChecker resultOK = cieCheckerInterface.verifyExpirationCie(validationData.getCieMrtd().getDg1());
        log.info("Risultato atteso OK -> " + resultOK.getValue());
        assertNotEquals(OK, resultOK.getValue());
    }

//    @Test
//    void extractCscaAnchorFromZipPathTest() {
//
//        List<X509Certificate> x509List = ValidateUtils.extractCscaAnchorFromZipPath(Path.of(cscaAnchorZipFile.getCscaAnchorPathFileName()));
//        Assertions.assertFalse(x509List.isEmpty());
//        log.info("x509List.size: {}" , x509List.size());
//    }


    @Test
    void extractS3ComponentsTest(){
        String inputUri = "s3://dgs-temp-089813480515/IT_MasterListCSCA.zip";
        String[] stringArray = ValidateUtils.extractS3Components(inputUri); //masterListCSCAZip_S3); //  inputUri);
        //InputStream fileInputStream = cieCheckerInterface.getContentCscaAnchorFile(inputUri); //getContentCscaAnchorFile(this.getCscaAnchorPathFileName());
        //List<X509Certificate> x509CertList  = ValidateUtils.extractCscaAnchorFromZip(fileInputStream);
        for(String a : stringArray) {
            log.info("stringArray {} ", a);
        }
        Assertions.assertNotNull(stringArray);
    }



    public static byte[] hexFile(String toHex) throws DecoderException {
        return Hex.decodeHex(toHex);
    }

    public static String cleanString(Path file) throws IOException {
        return Files.readString(file).replaceAll("\\s+", "");
    }

    @Test
    void checkExtractChallengeTest() {

        log.info("TEST verifyChallengeFromSignature - INIT... ");
        assertNotNull(validationData.getCieIas().getPublicKey());
        assertNotNull(validationData.getSignedNonce());
        assertNotNull(validationData.getNonce());

        //ResultCieChecker result = cieCheckerInterface.verifyChallengeFromSignature(validationData);
        //log.info("Risultato atteso OK -> {}", result.getValue());
        //assertNotEquals(OK, result.getValue());
        assertThrows(CieCheckerException.class,
                () -> cieCheckerInterface.verifyChallengeFromSignature(validationData));
        log.info("TEST verifyChallengeFromSignature - END ");
    }

    @Test
    void generatedPublicKeyTest() throws NoSuchAlgorithmException, InvalidKeySpecException {

        log.info("TEST generatedPublicKey - INIT... ");
        assertNotNull(validationData.getCieIas().getPublicKey());

        org.bouncycastle.asn1.pkcs.RSAPublicKey pkcs1PublicKey = org.bouncycastle.asn1.pkcs.RSAPublicKey.getInstance(validationData.getCieIas().getPublicKey()); ///nisPubKey);
        BigInteger modulus = pkcs1PublicKey.getModulus();
        BigInteger publicExponent = pkcs1PublicKey.getPublicExponent();
        RSAPublicKeySpec keySpec = new RSAPublicKeySpec(modulus, publicExponent);
        KeyFactory kf = KeyFactory.getInstance(RSA_ALGORITHM);
        PublicKey generatedPublic = kf.generatePublic(keySpec);

        log.info("Modulus: %X%n {}", modulus);
        log.info("See, Java class result: %s {}, is RSAPublicKey: %b%n {}", generatedPublic.getClass().getName(), generatedPublic instanceof RSAPublicKey);

        assertTrue(generatedPublic instanceof RSAPublicKey);
        assertEquals(modulus, ((RSAPublicKey)generatedPublic).getModulus());
        assertEquals(publicExponent, pkcs1PublicKey.getPublicExponent());
        log.info("TEST generatedPublic - END ");
    }

    @Test
    void testVerifyIntegrityDG_NotFound() {

        log.info("TEST testVerifyIntegrityDG_NotFound - INIT ");
        CieMrtd mrtd = validationData.getCieMrtd();
        mrtd.setDg1(null);
        mrtd.setDg11(null);

        log.info("Risultato atteso KO -> Validation error in verifyIntegrity: One ore more digest not found");
        // Verifica integrità
        assertThrows(CieCheckerException.class,
                () -> cieCheckerInterface.verifyIntegrity(mrtd));

        log.info("TEST testVerifyIntegrityDG_NotFound - END ");
    }

    @Test
    void testVerifyIntegritySOD_null() {

        log.info("TEST testVerifyIntegritySOD_null - INIT ");
        CieMrtd mrtd = validationData.getCieMrtd();
        mrtd.setSod(null);

        log.info("Risultato atteso KO -> Validation error in verifyIntegrity: Mrtd SOD is empty: must be present");
        // Verifica integrità
        assertThrows(CieCheckerException.class,
                () -> cieCheckerInterface.verifyIntegrity(mrtd));

        log.info("TEST testVerifyIntegritySOD_null - END ");
    }
/*
    @Test
    void testVerifyIntegrityFailDG1() throws Exception {
        byte[] sod = Files.readAllBytes(sodFile);

        // DG1 corrotto (modifica 1 byte)
        byte[] dg1 = Files.readAllBytes(dg1FilesCorrupted);

        CieMrtd mrtd = new CieMrtd();
        mrtd.setSod(sod);
        mrtd.setDg1(dg1);
        mrtd.setDg11(dg1);

        ResultCieChecker result = cieChecker.verifyIntegrity(mrtd);
        assertEquals(ResultCieChecker.KO_EXC_NOT_SAME_DIGEST, result, "Digest mismatch between expected and actual DG");
        //assertFalse(ResultCieChecker.OK.getValue().equals(OK));
    }


    @Test
    void testVerifyIntegrityFailDG11() throws Exception {
        byte[] sod = Files.readAllBytes(sodFile);

        byte[] dg1 = Files.readAllBytes(dg1Files);

        // DG11 corrotto (mancano dei caratteri)
        byte[] dg11 = Files.readAllBytes(dg11FilesCorroupted);

        CieMrtd mrtd = new CieMrtd();
        mrtd.setSod(sod);
        mrtd.setDg1(dg1);
        mrtd.setDg11(dg11);

        ResultCieChecker result = cieChecker.verifyIntegrity(mrtd);
        assertEquals(ResultCieChecker.KO_EXC_NOT_SAME_DIGEST, result, "DG11 corrotto deve dare KO");
    }

     */

    @ParameterizedTest(name = "Verifica digital signature con sorgente: {0}")
    @MethodSource("cieSources")
    void verifyDigitalSignature(String tipo, byte[] sodBytes) throws CMSException, Exception {
        log.info("=== INIZIO TEST [" + tipo + "] ===");
        //if(cieChecker.getCscaAnchor() == null )
        InputStream fileInputStream = new FileInputStream(Path.of(CSCA_ANCHOR_PATH_FILENAME).toFile());
        List<X509Certificate> cscaAnchor =  ValidateUtils.extractCscaAnchorFromZip(fileInputStream);
        //List<X509Certificate> cscaAnchor = cieCheckerInterface.extractCscaAnchor(CSCA_ANCHOR_PATH_FILENAME);
        log.info("cscaAnchor 1: {}" , cscaAnchor.size());
        // caso ok
        CMSSignedData cms = new CMSSignedData(sodBytes);
        ResultCieChecker resultOk = cieCheckerInterface.verifyDigitalSignature(cms);
        log.info("[" + tipo + "] - Risultato atteso OK -> " + resultOk.getValue());
        assertEquals("OK", resultOk.getValue());

        // caso ko: SOD nullo
        log.info("[" + tipo + "] - Test con SOD nullo");
        assertThrows(CieCheckerException.class,
                () -> cieCheckerInterface.verifyDigitalSignature(null));

        // caso ko: anchors null
        log.info("[" + tipo + "] - Test con anchors null");
        cieCheckerInterface.setCscaAnchor(null);
        log.info("cscaAnchor SET: {}" , cieCheckerInterface.getCscaAnchor());
        assertThrows(Exception.class,
               () -> cieCheckerInterface.verifyDigitalSignature(cms)); //, null));

        // caso ko: SOD non corretto
        log.info("[" + tipo + "] - Test con SOD non corretto");
        String efSodPem = """
        -----BEGIN PUBLIC KEY-----
        MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAytYrOy71s5KcL8FpSOwC
        MI/6+ZyaZkjMMbl/BDBtC59hlt8q5CptJihGqaRl5LeLJG7OqMfRteLtpHmsac5r
        ZmTUncm+mCPMKy1p8EDpYscHneyFGnbbSyH9xKt8QLHV/O8d96dGl/iYNsk7wF8R
        ihEy62qwfVUgeqhpaVNfEg1FYSOLLbR9OcBKRLamZcJrOqd5vuGNHZKyToqoWqhS
        ZntbKyZIC93ibnLiQkhetPnrZoCm1s81v8EW6ASbhpWaJEcv3xwe9nZxqjr9tMkO
        x9sOAT7gIN2hBQZasVxeCelfCZRjyh+P0j37DMpBaPCMlWLUeYQgKrd+aJty
        /QIDAQAB
        -----END PUBLIC KEY-----
        """;
        byte[] sodErrato = decodePublicKeyPemToDer(efSodPem);
        assertThrows(CMSException.class,
                () -> new CMSSignedData(sodErrato));

//        CMSSignedData cmsWithSodErrato = new CMSSignedData(sodErrato);
//        Assertions.assertThrows(CMSException.class,
//                () -> cieChecker.verifyDigitalSignature(cmsWithSodErrato));

//        // caso ko: blob corrotto : questo test non sussiste perchè l'eccezione scatterebbe sul 'generateCertificate'
//        List<byte[]> blobZip = new ArrayList<>();
//        List<X509Certificate> anchorZip = cieChecker.extractCscaAnchor();
//        for( X509Certificate x : anchorZip){
//            blobZip.add(x.getEncoded());
//        }
//        String concatenatedPem = blobZip.stream()
//                .map(d -> new String(toPem(d), StandardCharsets.UTF_8))
//                .collect(Collectors.joining());
//        byte[] caBlobZip = concatenatedPem.getBytes(StandardCharsets.UTF_8);
//        log.info("[" + tipo + "] - Test con blob corrotto");
//        byte[] blobErrato = ArrayUtils.addAll(sodBytes, caBlobZip);
//        var cf = CertificateFactory.getInstance(X_509);
//        X509Certificate ca = (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(blobErrato));
//        cieChecker.setCscaAnchor(List.of(ca));
//        Assertions.assertThrows(CieCheckerException.class,
//                () -> cieChecker.verifyDigitalSignature(sodBytes));

        log.info("=== FINE TEST [" + tipo + "] ===");
    }

    private static Stream<Arguments> cieSources() throws IOException, DecoderException {
        return Stream.of(
                Arguments.of("CIE MRTD",loadSodBytes(basePath.resolve(EF_SOD_HEX))),
                Arguments.of("CIE IAS", loadSodBytes(basePath.resolve(SOD_HEX_IAS)))
        );
    }

    private static byte[] loadSodBytes(Path filePath) throws IOException, DecoderException {
        String fileString = Files.readString(filePath).replaceAll("\\s+", "");
        return hexFile(fileString.substring(8));
    }


    private static byte[] toPem(byte[] der) {
        String b64 = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(der);
        String pem = "-----BEGIN CERTIFICATE-----\n" + b64 + "\n-----END CERTIFICATE-----\n";
        return pem.getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] decodePublicKeyPemToDer(String pem) {
        // Strip header/footer and whitespace, then Base64-decode
        String b64 = pem.replaceAll("-----BEGIN PUBLIC KEY-----", "")
                .replaceAll("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(b64);
    }


    @Test
    void verifySodPassiveAuthCie() throws CMSException {
        log.info("TEST verifySodPassiveAuthCie - INIT ");

        assertNotNull(validationData.getCieIas());
        assertNotNull(validationData.getCieIas().getSod());
        assertNotNull(validationData.getCieIas().getNis());

        assertTrue(cieCheckerInterface.verifySodPassiveAuthCie(new CMSSignedData(validationData.getCieIas().getSod()), validationData.getCieIas().getNis()));
        log.info("TEST verifySodPassiveAuthCie - END ");
    }

/// INIT TEST LETTURA FILE ZIP DELLA CATENA DI CERTIFICATI E VALIDAZIONE
    @Test
    void verifyDscAgainstAnchorBytes_derDsc_pemZIP_true() throws Exception {
        log.info("TEST verifyDscAgainstAnchorBytes_derDsc_pemZIP_true - INIT ");

        byte[] pkcs7 = loadSodBytes(basePath.resolve(SOD_HEX_IAS));
        assertNotNull(pkcs7);
        CMSSignedData cms = new CMSSignedData(pkcs7);
        X509CertificateHolder certHolder = ValidateUtils.extractDscCertDer(cms);
        byte[] dscDer = certHolder.getEncoded();
        List<X509Certificate> cscaAnchor = cieCheckerInterface.getCscaAnchor();   //extractCscaAnchor();
System.out.println("cscaAnchor 3: " + cscaAnchor);
        ResultCieChecker result =
                ValidateUtils.verifyDscAgainstTrustBundle(dscDer, cscaAnchor, null);

        log.info("Risultato atteso OK -> " + result.getValue());
        assertEquals(OK, result.getValue());

        log.info("TEST verifyDscAgainstAnchorBytes_derDsc_pemZIP_true - END ");

    }


    @Test
    void verifyDscAgainstAnchorBytes_pemDsc_pemBundle_true() throws Exception {

        byte[] pkcs7 = loadSodBytes(basePath.resolve(SOD_HEX_IAS));
        CMSSignedData cms = new CMSSignedData(pkcs7);
        X509CertificateHolder certHolder = ValidateUtils.extractDscCertDer(cms);
        byte[] dscPem = toPem(certHolder.getEncoded());
        cieCheckerInterface.setCscaAnchor(cieCheckerInterface.getCscaAnchor()); ///extractCscaAnchor());
        System.out.println("cscaAnchor 4 : " + cieCheckerInterface.getCscaAnchor());
        ResultCieChecker resultCieChecker =ValidateUtils.verifyDscAgainstTrustBundle(dscPem, cieCheckerInterface.getCscaAnchor(), null);
        log.info("TEST resultCieChecker: " + resultCieChecker.getValue());

        assertEquals(OK, resultCieChecker.getValue());
    }


    @Test
    void verifyDscAgainstAnchorBytes_false_when_all_parents_removed() throws Exception {
        var cf = CertificateFactory.getInstance(X_509);

        // DSC (DER)
        byte[] pkcs7 = loadSodBytes(basePath.resolve(SOD_HEX_IAS));
        CMSSignedData cms = new CMSSignedData(pkcs7);
        X509CertificateHolder certHolder = ValidateUtils.extractDscCertDer(cms);
        byte[] dscDer = certHolder.getEncoded();
        X509Certificate dscX509 = (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(dscDer));

        // Anchors
        //List<byte[]> anchorsDer = pickManyDerFromResources(-1);
        List<byte[]> anchorsDer = new ArrayList<>();
        List<X509Certificate> anchorZip = cieCheckerInterface.getCscaAnchor();
        for( X509Certificate x : anchorZip){
            anchorsDer.add(x.getEncoded());
        }

        // filtering all valid anchors
        List<byte[]> wrongAnchorBlobs = new ArrayList<>();
        List<X509Certificate> wrongAnchorX509 = new ArrayList<>();
        int removed = 0;
        for (byte[] der : anchorsDer) {
            X509Certificate ca = (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(der));
            boolean verifies;
            try {
                dscX509.verify(ca.getPublicKey());
                verifies = true;
            } catch (Exception e) {
                verifies = false;
            }
            if (!verifies) {
                wrongAnchorBlobs.add(der);
                wrongAnchorX509.add(ca);
            } else
                removed++;
        }
        assertTrue(removed > 0);

        //ResultCieChecker resultCieChecker = ValidateUtils.verifyDscAgainstAnchorBytes(dscDer, wrongAnchorBlobs, null);
        //Assertions.assertFalse(resultCieChecker.getValue().equals(OK));
        assertThrows(CieCheckerException.class,
                () -> ValidateUtils.verifyDscAgainstTrustBundle(dscDer, wrongAnchorX509, null));
    }

    @Test
    void verifyDscAgainstAnchorBytes_edgeCases() throws Exception {

        log.info("TEST verifyDscAgainstAnchorBytes_edgeCases - INIT ");
        // Anchors
        List<byte[]> ders = new ArrayList<>();
        for( X509Certificate x : cieCheckerInterface.getCscaAnchor()){
            ders.add(x.getEncoded());
        }
        var cf = CertificateFactory.getInstance(X_509);
        String concatenatedPem = ders.stream()
                .map(d -> new String(toPem(d), StandardCharsets.UTF_8))
                .collect(Collectors.joining());
        byte[] caBlob = concatenatedPem.getBytes(StandardCharsets.UTF_8);
        X509Certificate ca = (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(caBlob));

        //Sod null
        //ResultCieChecker resultSODNull =ValidateUtils.verifyDscAgainstAnchorBytes(null, List.of(caBlob), null);
        //Assertions.assertFalse(resultSODNull.getValue().equals(OK));
        assertThrows(CieCheckerException.class,
                () ->ValidateUtils.verifyDscAgainstTrustBundle(null, List.of(ca), null));

        // anchors null
        byte[] pkcs7 = loadSodBytes(basePath.resolve(SOD_HEX_IAS));
        CMSSignedData cms = new CMSSignedData(pkcs7);
        X509CertificateHolder certHolder = ValidateUtils.extractDscCertDer(cms);
        byte[] dscDer = certHolder.getEncoded();
        //ResultCieChecker resultNull = ValidateUtils.verifyDscAgainstAnchorBytes(dscDer, null, null);
        //Assertions.assertFalse(resultNull.getValue().equals(OK));
        assertThrows(CieCheckerException.class,
                () ->ValidateUtils.verifyDscAgainstTrustBundle(dscDer, null, null));

        // anchors empty
        //ResultCieChecker resultEmpty = ValidateUtils.verifyDscAgainstAnchorBytes(dscDer, List.of(), null);
        //Assertions.assertFalse(resultEmpty.getValue().equals(OK));
        assertThrows(CieCheckerException.class,
                () -> ValidateUtils.verifyDscAgainstTrustBundle(dscDer, List.of(), null));

        // anchor malformed
        byte[] malformed = "INVALID".getBytes(StandardCharsets.UTF_8);
        //ResultCieChecker resultMalformed = ValidateUtils.verifyDscAgainstAnchorBytes(dscDer, List.of(malformed), null);
        //Assertions.assertFalse(resultMalformed.getValue().equals(OK));
        assertThrows(CertificateException.class, () -> cf.generateCertificate(new ByteArrayInputStream(malformed)) );

        /*X509Certificate caMalFormed = (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(malformed));
        Assertions.assertThrows(CieCheckerException.class,
                () -> ValidateUtils.verifyDscAgainstAnchorBytes(dscDer, List.of(caMalFormed), null));

         */
        log.info("TEST verifyDscAgainstAnchorBytes_edgeCases - END ");
    }

    /// fine test verifyDscAgainstAnchorBytes partendo dal file ZIP

// NON FUNZIONA PER INPUT ERRATO DG1 e DG11
    @Test
    public void testVerifyIntegrity()  {
        log.info("TEST testVerifyIntegrityOk - INIT ");


        assertThrows(CieCheckerException.class,
                () -> cieCheckerInterface.verifyIntegrity(validationData.getCieMrtd()));
        log.info("TEST testVerifyIntegrityOk - END ");
    }


    @Test
    void mergeTest() throws FileNotFoundException {

        String cscaPath = "s3://pn-runtime-environment-variables-eu-south-1-830192246553/pn-mandate/csca-masterlist/";

        //Include nello ZIP file "/new_IT_MasterListCSCA.zip" il file "/catest.pem"
        MasterListMergeToolUtility master = new MasterListMergeToolUtility(s3BucketClient, cscaPath);

        InputStream fileInputStream = new FileInputStream(Path.of(CSCA_ANCHOR_PATH_FILENAME).toFile());
        ResponseInputStream<GetObjectResponse> zipS3Stream = new ResponseInputStream<>(
                GetObjectResponse.builder().build(),
                AbortableInputStream.create(fileInputStream)
        );

        InputStream fileInputStreamPem = new FileInputStream(Path.of(fileToAddMasterListZip).toFile());
        ResponseInputStream<GetObjectResponse> pemS3Stream = new ResponseInputStream<>(
                GetObjectResponse.builder().build(),
                AbortableInputStream.create(fileInputStreamPem)
        );
        when(s3BucketClient.getObjectContent(anyString()))
                // ...la prima volta restituisci lo ZIP
                .thenReturn(zipS3Stream)
                // ...la seconda volta restituisci il PEM
                .thenReturn(pemS3Stream);

        ResultCieChecker result = master.merge(); //originalMasterListZip, fileToAddMasterListZip);
        Assertions.assertTrue(result.getValue().equals(OK));
    }


    @Test
    void uploadContentTest() throws Exception {

        String cscaPath = "s3://pn-runtime-environment-variables-eu-south-1-830192246553/pn-mandate/csca-masterlist";

        //Update del file "/new_IT_MasterListCSCA.zip"
        MasterListMergeToolUtility master = new MasterListMergeToolUtility(s3BucketClient, cscaPath );
        File newFileMaster = new File("src/test/resources/new_IT_MasterListCSCA.zip");
        AbortableInputStream inStream =
                AbortableInputStream.create(new FileInputStream(newFileMaster));

        when(clientS3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
        ResultCieChecker result = master.writeNewMasterZip(inStream);
        Assertions.assertTrue(result.getValue().equals(OK));

        log.info("TEST writeNewMasterZip - END ");
    }


}