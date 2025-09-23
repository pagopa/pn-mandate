package it.pagopa.pn.mandate.validation;

import it.pagopa.pn.ciechecker.CieChecker;
import it.pagopa.pn.ciechecker.CieCheckerImpl;
import it.pagopa.pn.ciechecker.exception.CieCheckerException;
import it.pagopa.pn.ciechecker.model.CieMrtd;
import it.pagopa.pn.ciechecker.model.ResultCieChecker;
import it.pagopa.pn.ciechecker.model.CieIas;
import it.pagopa.pn.ciechecker.model.CieValidationData;
import it.pagopa.pn.ciechecker.utils.ValidateUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.crypto.CryptoException;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.OperatorCreationException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;


import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
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


@Slf4j
class CieCheckerTest {

    private static CieChecker cieChecker;
    private static ValidateUtils validateUtils;
    private static final Path basePath= Path.of("src","test","resources");
    private static final Path sodFile = Paths.get("src/test/resources/EF.SOD");
    private static final Path dg1Files = Paths.get("src/test/resources/EF.DG1");
    private static final Path dg11Files = Paths.get("src/test/resources/EF.DG11");
    private static final Path dg1FilesCorrupted = Paths.get("src/test/resources/DG1_CORROTTO.HEX");
    private static final Path dg11FilesCorroupted = Paths.get("src/test/resources/DG11_CORROTTO.HEX");
    private static final List<String> compatibleAlgorithms = List.of(SHA_256,SHA_384,SHA_512);
    private static final String SOD_HEX_IAS = "SOD_IAS.HEX";
    private static final Path CSCA_DIR = Path.of("src","test","resources","csca");
    private static final String EF_SOD_HEX = "EF_SOD.HEX";
    private static final Path BASE_PATH = Path.of("src","test","resources");


    @BeforeAll
    static void setUp() {
        cieChecker = new CieCheckerImpl();
        cieChecker.init();

        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private static byte[] hexFile(String toHex) throws DecoderException {
        return Hex.decodeHex(toHex);
    }

    private static String cleanString(Path file) throws IOException {
        return Files.readString(file).replaceAll("\\s+", "");
    }

    @Test
    void checkExtractChallengeTest() throws IOException, DecoderException, NoSuchAlgorithmException, InvalidKeySpecException, CryptoException {
        //byte[] nisChallenge = hexFile(cleanString(basePath.resolve("NIS_CHALLENGE.HEX")));
        byte[] nisPubKey = hexFile(cleanString(basePath.resolve("NIS_PUBKEY.HEX")));
        byte[] nisSignature = hexFile(cleanString(basePath.resolve("NIS_SIGNATURE.HEX")));
        //byte[] sodBytesMalformed = hexFile(cleanString(basePath.resolve("EF_SOD.HEX")));
        //byte[] sodBytes = hexFile(cleanString(basePath.resolve("SOD_IAS.HEX"))); //no
        //byte[] sodBytes = hexFile(cleanString(basePath.resolve("SOD.HEX")));

        //String fileStringSod = Files.readString(basePath.resolve("SOD_IAS.HEX")).replaceAll("\\s+", "");
        //String subString = fileStringSod.substring(8, fileStringSod.length());
        //byte[] sodBytes = hexFile(subString);

        CieValidationData data = new CieValidationData();
        CieIas cieIas = new CieIas();
        cieIas.setPublicKey(nisPubKey);
        data.setCieIas(cieIas);
        data.setSignedNonce(nisSignature);
        data.setNonce("D3FFB7DE52E211AC69B9DE70996E46F5"); //nis_challenge.hex
        //Assertions.assertTrue(cieChecker.verifyChallengeFromSignature(nisSignature, nisPubKey,nisChallenge));
        ResultCieChecker result = cieChecker.verifyChallengeFromSignature(data);
        Assertions.assertTrue(result.getValue().equals(OK));

    }

    @Test
    void decodeTest() throws NoSuchAlgorithmException, InvalidKeySpecException, IOException, DecoderException {
        byte[] decoded = hexFile(cleanString(basePath.resolve("NIS_PUBKEY.HEX")));
        org.bouncycastle.asn1.pkcs.RSAPublicKey pkcs1PublicKey = org.bouncycastle.asn1.pkcs.RSAPublicKey.getInstance(decoded);
        BigInteger modulus = pkcs1PublicKey.getModulus();
        BigInteger publicExponent = pkcs1PublicKey.getPublicExponent();
        RSAPublicKeySpec keySpec = new RSAPublicKeySpec(modulus, publicExponent);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        PublicKey generatedPublic = kf.generatePublic(keySpec);
        System.out.printf("Modulus: %X%n", modulus);
        System.out.printf("See, Java class result: %s, is RSAPublicKey: %b%n", generatedPublic.getClass().getName(), generatedPublic instanceof RSAPublicKey);
        Assertions.assertTrue(generatedPublic instanceof RSAPublicKey);
        Assertions.assertEquals(modulus, ((RSAPublicKey)generatedPublic).getModulus());
        Assertions.assertEquals(publicExponent, pkcs1PublicKey.getPublicExponent());
    }

    /*
    @Test
    void testVerifyIntegrityDG_NotFound() throws Exception {
        // Legge il SOD binario
        byte[] sod = Files.readAllBytes(sodFile);

        CieMrtd mrtd = new CieMrtd();
        mrtd.setSod(sod);
        mrtd.setDg1(null);
        mrtd.setDg11(null);

        // Verifica integrità
        ResultCieChecker result = cieChecker.verifyIntegrity(mrtd);

        assertEquals(ResultCieChecker.KO_EXC_NOTFOUND_DIGEST_SOD, result);
    }

    @Test
    void testVerifyIntegritySOD_null() throws Exception {
        // Legge il SOD binario
        byte[] sod = null;

        byte[] dg1 = Files.readAllBytes(dg1Files);

        CieMrtd mrtd = new CieMrtd();
        mrtd.setSod(sod);
        mrtd.setDg1(dg1);
        mrtd.setDg11(null);

        // Verifica integrità
        ResultCieChecker result = cieChecker.verifyIntegrity(mrtd);

        assertEquals(ResultCieChecker.KO_EXC_NOTFOUND_MRTD_SOD, result);
    }

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
    void verifyDigitalSignature(String tipo, byte[] sodBytes) throws Exception {
        System.out.println("=== INIZIO TEST [" + tipo + "] ===");

        // concatenazione certificati DER in formato PEM
 /*       List<byte[]> ders = pickManyDerFromResources(-1);
        String concatenatedPem = ders.stream()
                .map(d -> new String(toPem(d), StandardCharsets.UTF_8))
                .collect(Collectors.joining());
        byte[] blob = concatenatedPem.getBytes(StandardCharsets.UTF_8);
*/
        cieChecker.setCscaAnchor(cieChecker.extractCscaAnchor());
        // caso ok
        ResultCieChecker resultOk = cieChecker.verifyDigitalSignature(sodBytes);
        System.out.println("[" + tipo + "] - Risultato atteso OK -> " + resultOk.getValue());
        Assertions.assertEquals("OK", resultOk.getValue());

        // caso ko: SOD nullo
        System.out.println("[" + tipo + "] - Test con SOD nullo");
        Assertions.assertThrows(CieCheckerException.class,
                () -> cieChecker.verifyDigitalSignature(null));

        // caso ko: anchors null
        System.out.println("[" + tipo + "] - Test con anchors null");
        cieChecker.setCscaAnchor(null);
        Assertions.assertThrows(CieCheckerException.class,
               () -> cieChecker.verifyDigitalSignature(sodBytes)); //, null));

        // caso ko: SOD non corretto
        System.out.println("[" + tipo + "] - Test con SOD non corretto");
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
        Assertions.assertThrows(CieCheckerException.class,
                () -> cieChecker.verifyDigitalSignature(sodErrato));

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
//        System.out.println("[" + tipo + "] - Test con blob corrotto");
//        byte[] blobErrato = ArrayUtils.addAll(sodBytes, caBlobZip);
//        var cf = CertificateFactory.getInstance(X_509);
//        X509Certificate ca = (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(blobErrato));
//        cieChecker.setCscaAnchor(List.of(ca));
//        Assertions.assertThrows(CieCheckerException.class,
//                () -> cieChecker.verifyDigitalSignature(sodBytes));

        System.out.println("=== FINE TEST [" + tipo + "] ===");
    }

    private static Stream<Arguments> cieSources() throws IOException, DecoderException {
        return Stream.of(
                Arguments.of("CIE MRTD",loadSodBytes(BASE_PATH.resolve(EF_SOD_HEX))),
                Arguments.of("CIE IAS", loadSodBytes(BASE_PATH.resolve(SOD_HEX_IAS)))
        );
    }

    private static byte[] loadSodBytes(Path filePath) throws IOException, DecoderException {
        String fileString = Files.readString(filePath).replaceAll("\\s+", "");
        return hexFile(fileString.substring(8));
    }


    @Test
    void validateMandateTest() throws Exception {

        byte[] nisPubKey = hexFile(cleanString(basePath.resolve("NIS_PUBKEY.HEX")));
        byte[] nisSignature = hexFile(cleanString(basePath.resolve("NIS_SIGNATURE.HEX")));
        byte[] nisHexToCheck = hexFile("393130373138343634363534");
        String nonce = "D3FFB7DE52E211AC69B9DE70996E46F5";
        String fileString = Files.readString(BASE_PATH.resolve(SOD_HEX_IAS)).replaceAll("\\s+", "");
        String subString = fileString.substring(8, fileString.length());
        byte[] sodIasByteArray = hexFile(subString);
        byte[] sodMrtd = Files.readAllBytes(sodFile);
        byte[] dg1 = Files.readAllBytes(dg1Files);
        byte[] dg11 = Files.readAllBytes(dg11Files);

        CieValidationData validationData = new CieValidationData();
        CieIas cIas = new CieIas();
        cIas.setPublicKey(nisPubKey);
        cIas.setNis(nisHexToCheck);
        cIas.setSod(sodIasByteArray);

        CieMrtd cMrtd = new CieMrtd();
        cMrtd.setSod(sodMrtd);
        cMrtd.setDg1(dg1);
        cMrtd.setDg11(dg11);

        validationData.setCieIas(cIas);
        validationData.setSignedNonce(nisSignature);
        validationData.setNonce(nonce); //nis_challenge.hex
        validationData.setCieMrtd(cMrtd);

/*        List<byte[]> ders = pickManyDerFromResources(-1);
        String concatenatedPem = ders.stream()
                .map(d->new String(toPem(d), StandardCharsets.UTF_8))
                .collect(Collectors.joining());
        byte[] blob = concatenatedPem.getBytes(StandardCharsets.UTF_8);
*/

        cieChecker.setCscaAnchor(cieChecker.extractCscaAnchor());
        ResultCieChecker result = cieChecker.validateMandate( validationData);
        log.info("result validateMandate: " + result.getValue());
        Assertions.assertTrue(result.getValue().equals(OK));
    }

    public static List<byte[]> pickManyDerFromResources(int n) throws Exception {
        List<Path> ders = listDerFiles();
        Assertions.assertTrue(ders.size() >= n);
        List<byte[]> out = new ArrayList<>();
        if ( n == -1 ){
            for (Path der : ders) {
                out.add(Files.readAllBytes(der));
            }
        } else {
            for (int i = 0; i < n; i++) out.add(Files.readAllBytes(ders.get(i)));
        }
        return out;
    }

    public static byte[] toPem(byte[] der) {
        String b64 = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(der);
        String pem = "-----BEGIN CERTIFICATE-----\n" + b64 + "\n-----END CERTIFICATE-----\n";
        return pem.getBytes(StandardCharsets.US_ASCII);
    }
    private static List<Path> listDerFiles() throws Exception {
        try (var s = Files.list(CSCA_DIR)) {
            return s.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cer"))
                    .collect(Collectors.toList());
        }
    }

    public static byte[] decodePublicKeyPemToDer(String pem) {
        // Strip header/footer and whitespace, then Base64-decode
        String b64 = pem.replaceAll("-----BEGIN PUBLIC KEY-----", "")
                .replaceAll("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(b64);
    }


    @Test
    void verifySodPassiveAuthCie() throws IOException, DecoderException, CMSException, CertificateException, OperatorCreationException {
        System.out.println("TEST verificationSodCie - INIT");
        System.out.println(" - Leggo il file SOD_IAS_FILENAME e decodifico in byte[] HEX");
        String fileString = Files.readString(basePath.resolve("SOD_IAS.HEX")).replaceAll("\\s+", "");
        String subString = fileString.substring(8, fileString.length());
        byte[] sodIasByteArray = hexFile(subString);

        byte[] nisHexToCheck = hexFile("393130373138343634363534");

        CieIas cie = new CieIas();
        cie.setSod(sodIasByteArray);

        System.out.println(" - Leggo il file NIS_IAS_FILENAME e decodifico in byte[] HEX");
        //byte[] nisHexToCheck = hexFile(NIS_HEX_TO_CHECK);
        //System.out.println("DECODED_NIS_STRING : " + nisHexToCheck);
        cie.setNis(nisHexToCheck);
        Assertions.assertTrue(cieChecker.verifySodPassiveAuthCie(cie));
    }

/// INIT TEST LETTURA FILE ZIP DELLA CATENA DI CERTIFICATI E VALIDAZIONE
    @Test
    void verifyDscAgainstAnchorBytes_derDsc_pemZIP_true() throws Exception {

        byte[] pkcs7 = extractCertificateByteArray();
        X509CertificateHolder holder = ValidateUtils.extractDscCertDer(pkcs7);
        byte[] dscDer = holder.getEncoded();

        ResultCieChecker resultCieChecker =
                ValidateUtils.verifyDscAgainstAnchorBytes(dscDer, cieChecker.extractCscaAnchor(), null);
        System.out.println("TEST resultCieChecker: " + resultCieChecker.getValue());
        Assertions.assertTrue(resultCieChecker.getValue().equals(OK));
    }

    private byte[] extractCertificateByteArray() throws DecoderException, IOException {
        String fileString = Files.readString(BASE_PATH.resolve("SOD_IAS.HEX")).replaceAll("\\s+", "");
        String subString = fileString.substring(8,fileString.length());
        return hexFile(subString);
    }

    @Test
    void verifyDscAgainstAnchorBytes_pemDsc_pemBundle_true() throws Exception {
        byte[] pkcs7 = extractCertificateByteArray();
        X509CertificateHolder holder = ValidateUtils.extractDscCertDer(pkcs7);
        byte[] dscPem = toPem(holder.getEncoded());

        ResultCieChecker resultCieChecker =ValidateUtils.verifyDscAgainstAnchorBytes(dscPem, cieChecker.extractCscaAnchor(), null);
        System.out.println("TEST resultCieChecker: " + resultCieChecker.getValue());

        Assertions.assertTrue(resultCieChecker.getValue().equals(OK));
    }


    @Test
    void verifyDscAgainstAnchorBytes_false_when_all_parents_removed() throws Exception {
        var cf = CertificateFactory.getInstance(X_509);

        // DSC (DER)
        byte[] pkcs7 = extractCertificateByteArray();
        X509CertificateHolder holder = ValidateUtils.extractDscCertDer(pkcs7);
        byte[] dscDer = holder.getEncoded();
        X509Certificate dscX509 = (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(dscDer));

        // Anchors
        //List<byte[]> anchorsDer = pickManyDerFromResources(-1);
        List<byte[]> anchorsDer = new ArrayList<>();
        List<X509Certificate> anchorZip = cieChecker.extractCscaAnchor();
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
        Assertions.assertTrue(removed > 0);

        //ResultCieChecker resultCieChecker = ValidateUtils.verifyDscAgainstAnchorBytes(dscDer, wrongAnchorBlobs, null);
        //Assertions.assertFalse(resultCieChecker.getValue().equals(OK));
        Assertions.assertThrows(CieCheckerException.class,
                () -> ValidateUtils.verifyDscAgainstAnchorBytes(dscDer, wrongAnchorX509, null));
    }

    @Test
    void verifyDscAgainstAnchorBytes_edgeCases() throws Exception {

        // Anchors
        //List<byte[]> ders = pickManyDerFromResources(-1);
        List<byte[]> ders = new ArrayList<>();
        List<X509Certificate> anchorZip = cieChecker.extractCscaAnchor();
        for( X509Certificate x : anchorZip){
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
        Assertions.assertThrows(CieCheckerException.class,
                () ->ValidateUtils.verifyDscAgainstAnchorBytes(null, List.of(ca), null));


        // anchors null
        byte[] pkcs7 = extractCertificateByteArray();
        byte[] dscDer = ValidateUtils.extractDscCertDer(pkcs7).getEncoded();
        //ResultCieChecker resultNull = ValidateUtils.verifyDscAgainstAnchorBytes(dscDer, null, null);
        //Assertions.assertFalse(resultNull.getValue().equals(OK));
        Assertions.assertThrows(CieCheckerException.class,
                () ->ValidateUtils.verifyDscAgainstAnchorBytes(dscDer, null, null));

        // anchors empty
        //ResultCieChecker resultEmpty = ValidateUtils.verifyDscAgainstAnchorBytes(dscDer, List.of(), null);
        //Assertions.assertFalse(resultEmpty.getValue().equals(OK));
        Assertions.assertThrows(CieCheckerException.class,
                () -> ValidateUtils.verifyDscAgainstAnchorBytes(dscDer, List.of(), null));

        // anchor malformed
        byte[] malformed = "INVALID".getBytes(StandardCharsets.UTF_8);
        //ResultCieChecker resultMalformed = ValidateUtils.verifyDscAgainstAnchorBytes(dscDer, List.of(malformed), null);
        //Assertions.assertFalse(resultMalformed.getValue().equals(OK));
        Assertions.assertThrows(CertificateException.class, () -> cf.generateCertificate(new ByteArrayInputStream(malformed)) );

        /*X509Certificate caMalFormed = (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(malformed));
        Assertions.assertThrows(CieCheckerException.class,
                () -> ValidateUtils.verifyDscAgainstAnchorBytes(dscDer, List.of(caMalFormed), null));

         */
    }

    /// fine test verifyDscAgainstAnchorBytes partendo dal file ZIP

// NON FUNZIONA PER INPUT ERRATO DG1 e DG11
    @Test
    public void testVerifyIntegrityOk() throws IOException {

        byte[] sodMrtd = Files.readAllBytes(sodFile);
        byte[] dg1 = Files.readAllBytes(dg1Files);
        byte[] dg11 = Files.readAllBytes(dg11Files);

        CieMrtd cMrtd = new CieMrtd();
        cMrtd.setSod(sodMrtd);
        cMrtd.setDg1(dg1);
        cMrtd.setDg11(dg11);

        ResultCieChecker result = cieChecker.verifyIntegrity(cMrtd);
        assertTrue(result.getValue().equals("OK"));  //, "Gli hash dei DG devono corrispondere a quelli del SOD");
    }

}