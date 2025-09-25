package it.pagopa.pn.mandate.validation;

import it.pagopa.pn.ciechecker.CieCheckerImpl;
import it.pagopa.pn.ciechecker.CieCheckerInterface;
import it.pagopa.pn.ciechecker.exception.CieCheckerException;
import it.pagopa.pn.ciechecker.model.CieMrtd;
import it.pagopa.pn.ciechecker.model.ResultCieChecker;
import it.pagopa.pn.ciechecker.model.CieIas;
import it.pagopa.pn.ciechecker.model.CieValidationData;
import it.pagopa.pn.ciechecker.utils.ValidateUtils;
import lombok.CustomLog;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSSignedData;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
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


@SpringBootTest(classes = it.pagopa.pn.ciechecker.CieCheckerImpl.class)
@Slf4j
@ActiveProfiles("test")
class CieCheckerTest {

    @Autowired
    private CieCheckerInterface cieChecker;
    @Autowired
    private CieCheckerImpl.CscaAnchorZipFile cscaAnchorZipFile;

    private static final Path basePath= Path.of("src","test","resources");
    private static final Path sodFile = Paths.get("src/test/resources/EF.SOD");
    private static final Path dg1Files = Paths.get("src/test/resources/EF.DG1");
    private static final Path dg11Files = Paths.get("src/test/resources/EF.DG11");
    private static final Path dg1FilesCorrupted = Paths.get("src/test/resources/DG1_CORROTTO.HEX");
    private static final Path dg11FilesCorroupted = Paths.get("src/test/resources/DG11_CORROTTO.HEX");
    private static final List<String> compatibleAlgorithms = List.of(SHA_256,SHA_384,SHA_512);
    private static final String SOD_HEX_IAS = "SOD_IAS.HEX";
    //private static final Path CSCA_DIR = Path.of("src","test","resources","csca");
    private static final String EF_SOD_HEX = "EF_SOD.HEX";

    static CieValidationData validationData;

    @BeforeEach
    void setUp() throws IOException, DecoderException {

        cieChecker.init();

        byte[] nisPubKey = hexFile(cleanString(basePath.resolve("NIS_PUBKEY.HEX")));
        byte[] nisSignature = hexFile(cleanString(basePath.resolve("NIS_SIGNATURE.HEX")));
        String nisChallenge = cleanString(basePath.resolve("NIS_CHALLENGE.HEX"));
        byte[] nisHexToCheck = hexFile(cleanString(basePath.resolve("NIS.HEX")));
        byte[] sodIasByteArray = loadSodBytes(basePath.resolve(SOD_HEX_IAS));

        byte[] sodMrtd = Files.readAllBytes(sodFile);
        byte[] dg1 = Files.readAllBytes(dg1Files);
        byte[] dg11 = Files.readAllBytes(dg11Files);

        validationData = new CieValidationData();
        CieIas cieIas = new CieIas();
        cieIas.setPublicKey(nisPubKey);
        cieIas.setNis(nisHexToCheck);
        cieIas.setSod(sodIasByteArray);

        validationData.setCieIas(cieIas);
        validationData.setSignedNonce(nisSignature);
        validationData.setNonce(nisChallenge);

        CieMrtd cMrtd = new CieMrtd();
        cMrtd.setSod(sodMrtd);
        cMrtd.setDg1(dg1);
        cMrtd.setDg11(dg11);
        validationData.setCieMrtd(cMrtd);

        // Assertions.assertTrue(cieChecker.validateDataInput(validationData));
    }


    @Test
    @Order(1)
    void validateMandateTest() throws IOException {
        log.info("TEST validateMandateTest - INIT... ");

        if(cieChecker.getCscaAnchor() == null) {
            //cieChecker.setCscaAnchor(cieChecker.extractCscaAnchor());
            log.debug("cscaAnchor SIZE: " + cieChecker.getCscaAnchor());
        }
        log.debug("cscaAnchor SIZE: " + cieChecker.getCscaAnchor().size());
        //log.info("DG11: " +validationData.getCieMrtd().getDg1());
        if(validationData.getCieMrtd().getDg1() == null )
            validationData.getCieMrtd().setDg1(Files.readAllBytes(dg1Files));
        //log.info("DG1: " +validationData.getCieMrtd().getDg1());

        //log.info("DG11: " +validationData.getCieMrtd().getDg11());
        if(validationData.getCieMrtd().getDg11() == null )
            validationData.getCieMrtd().setDg11(Files.readAllBytes(dg11Files));
        //log.info("DG11: " +validationData.getCieMrtd().getDg11());

        ResultCieChecker result = cieChecker.validateMandate( validationData);
        log.info("Risultato atteso OK -> " + result.getValue());

        assertEquals(OK, result.getValue());
        log.info("TEST validateMandateTest - END ");
    }

    @Test
    void extractCscaAnchorFromZipTest() {
        //CMSSignedData cms = ValidateUtils.loadCscaAnchorIntoCMS();
        List<X509Certificate> x509List = ValidateUtils.extractCscaAnchorFromZip(Path.of(cscaAnchorZipFile.getCscaAnchorPathFileName()));
        Assertions.assertFalse(x509List.isEmpty());
        log.info("x509List.size: {}" , x509List.size());
    }

    private static byte[] hexFile(String toHex) throws DecoderException {
        return Hex.decodeHex(toHex);
    }

    private static String cleanString(Path file) throws IOException {
        return Files.readString(file).replaceAll("\\s+", "");
    }

    @Test
    void checkExtractChallengeTest() {

        log.info("TEST verifyChallengeFromSignature - INIT... ");
        assertNotNull(validationData.getCieIas().getPublicKey());
        assertNotNull(validationData.getSignedNonce());
        assertNotNull(validationData.getNonce());

        ResultCieChecker result = cieChecker.verifyChallengeFromSignature(validationData);
        log.info("Risultato atteso OK -> {}", result.getValue());
        assertEquals(OK, result.getValue());
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
                () -> cieChecker.verifyIntegrity(mrtd));

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
                () -> cieChecker.verifyIntegrity(mrtd));

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
    void verifyDigitalSignature(String tipo, byte[] sodBytes) throws CMSException {
        log.info("=== INIZIO TEST [" + tipo + "] ===");
        //if(cieChecker.getCscaAnchor() == null )
        //    cieChecker.setCscaAnchor(cieChecker.extractCscaAnchor());
        log.info("cscaAnchor 1: {}" , cieChecker.getCscaAnchor().size());
        // caso ok
        CMSSignedData cms = new CMSSignedData(sodBytes);
        ResultCieChecker resultOk = cieChecker.verifyDigitalSignature(cms);
        log.info("[" + tipo + "] - Risultato atteso OK -> " + resultOk.getValue());
        assertEquals("OK", resultOk.getValue());

        // caso ko: SOD nullo
        log.info("[" + tipo + "] - Test con SOD nullo");
        assertThrows(CieCheckerException.class,
                () -> cieChecker.verifyDigitalSignature(null));

        // caso ko: anchors null
        log.info("[" + tipo + "] - Test con anchors null");
        cieChecker.setCscaAnchor(null);
        log.info("cscaAnchor SET: {}" , cieChecker.getCscaAnchor());
        assertThrows(Exception.class,
               () -> cieChecker.verifyDigitalSignature(cms)); //, null));

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

        assertTrue(cieChecker.verifySodPassiveAuthCie(new CMSSignedData(validationData.getCieIas().getSod()), validationData.getCieIas().getNis()));
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
        List<X509Certificate> cscaAnchor = cieChecker.getCscaAnchor();   //extractCscaAnchor();
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
        cieChecker.setCscaAnchor(cieChecker.getCscaAnchor()); ///extractCscaAnchor());
        System.out.println("cscaAnchor 4 : " + cieChecker.getCscaAnchor());
        ResultCieChecker resultCieChecker =ValidateUtils.verifyDscAgainstTrustBundle(dscPem, cieChecker.getCscaAnchor(), null);
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
        List<X509Certificate> anchorZip = cieChecker.getCscaAnchor();
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
        for( X509Certificate x : cieChecker.getCscaAnchor()){
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
    public void testVerifyIntegrityOk()  {
        log.info("TEST testVerifyIntegrityOk - INIT ");

        ResultCieChecker result = cieChecker.verifyIntegrity(validationData.getCieMrtd());
        log.info("Risultato atteso OK -> " + result.getValue());

        assertEquals(OK, result.getValue());  //, "Gli hash dei DG devono corrispondere a quelli del SOD");
        log.info("TEST testVerifyIntegrityOk - END ");
    }

}