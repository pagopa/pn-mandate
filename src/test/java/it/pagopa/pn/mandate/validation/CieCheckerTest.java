package it.pagopa.pn.mandate.validation;

import it.pagopa.pn.ciechecker.CieChecker;
import it.pagopa.pn.ciechecker.CieCheckerImpl;
import it.pagopa.pn.ciechecker.model.SodSummary;
import it.pagopa.pn.ciechecker.utils.ValidateUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.bouncycastle.crypto.CryptoException;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPublicKeySpec;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static it.pagopa.pn.ciechecker.CieCheckerConstants.*;
import static it.pagopa.pn.ciechecker.utils.ValidateUtils.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
class CieCheckerTest {

    private static CieChecker cieChecker;
    private static final Path basePath= Path.of("src","test","resources");
    private static final Path sodFile = Paths.get("src/test/resources/EF.SOD");
    private static final List<Path> dgFiles = List.of(Paths.get("src/test/resources/EF.DG1"));
    private static final List<Path> dgFilesCorrotto = List.of(Paths.get("src/test/resources/EF.DG1_CORROTTO"));
    private static final List<String> compatibleAlgorithms = List.of(SHA_256,SHA_384,SHA_512);

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
        byte[] nisChallenge = hexFile(cleanString(basePath.resolve("NIS_CHALLENGE.HEX")));
        byte[] nisPubKey = hexFile(cleanString(basePath.resolve("NIS_PUBKEY.HEX")));
        byte[] nisSignature = hexFile(cleanString(basePath.resolve("NIS_SIGNATURE.HEX")));

        Assertions.assertTrue(cieChecker.verifyChallengeFromSignature(nisSignature,nisPubKey,nisChallenge));
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


    @Test
    public void testVerifyIntegrityOk() {
        boolean result = cieChecker.verifyIntegrity(sodFile, dgFiles);
        assertTrue(result, "Gli hash dei DG devono corrispondere a quelli del SOD");
    }

    @Test
    public void testVerifyIntegrityFail() {
        boolean result = cieChecker.verifyIntegrity(sodFile, dgFilesCorrotto);
        assertFalse(result, "Con DG corrotto la verifica deve fallire");
    }


    @Test
    public void allVerificationCompleted() throws Exception {
        log.info("--- Inizio Verifica Integrità (Passive Authentication) ---");
        //Parse EF.SOD
        /*--- 1. Estrazione dei dati attesi dal SOD ---
         *  sod_summary=$(./decode_sod_hr.sh EF.SOD)
         */
        log.info("1. Analizzo il SOD per ottenere gli hash attesi...");
        SodSummary sodSummary = decodeSodHr(Files.readAllBytes(sodFile));
        //Identify hash Algo
        //hash_algo_name=$(echo "$sod_summary" | grep "Algoritmo di Hashing" | awk '{print $6}')          ###STAMPA algoritmo di hashing
        String hashAlgorithmName = getDigestName(sodSummary.getDgDigestAlgorithm().getAlgorithm().getId());
        //Verifies hash algo exists or exits.
        assertTrue(hashAlgorithmName != null && !hashAlgorithmName.isEmpty());

        //Retrieves expected hash
        Map<Integer,byte[]> expectedHashes = sodSummary.getDgExpectedHashes();
        assertNotNull(expectedHashes);
        assertFalse(expectedHashes.isEmpty());

        log.info("Expected hash : {}", expectedHashes);

        //Verifies if sha algorithm is supported or exits
        Assertions.assertNotNull(hashAlgorithmName); //verify hash algorithm compatibility
        assertTrue(compatibleAlgorithms.contains(hashAlgorithmName));
        log.info("Selected Algorithm: {}", hashAlgorithmName);
        //# --- 2. Verifica di ogni Data Group presente ---
        log.info("2. Calcolo e verifico l'hash per ogni Data Group trovato...");

        MessageDigest md = MessageDigest.getInstance(hashAlgorithmName);
        // For each EF.DG* file determine and validate the hash
        for (Path dgFilePath: dgFiles) {
            String fileName = dgFilePath.getFileName().toString();
            log.info("FileName: {}", fileName);
            Integer dgNum = extractDgNumber(fileName);
            log.info("DgNumber: {}", dgNum);
            //Verify that file exists
            Assertions.assertNotNull(dgNum);

            byte[] fileContent= Files.readAllBytes(dgFilePath);
            //verify that file is not empty
            assertTrue(fileContent.length > 0);

            //Calculate digest
            md.reset();
            byte[] actualDigest = md.digest(fileContent);

            //Expected
            byte[] expectedDigest = expectedHashes.get(dgNum);
            Assertions.assertNotNull(actualDigest);
            Assertions.assertNotNull(expectedDigest);

            //Verify Hash
            boolean isSameDigest = Arrays.equals(actualDigest, expectedDigest);
            log.info("DG{} -> expected={}, actual={}, esito={}",
                    dgNum,
                    org.bouncycastle.util.encoders.Hex.toHexString(expectedDigest),
                    org.bouncycastle.util.encoders.Hex.toHexString(actualDigest),
                    isSameDigest ? "OK" : "KO");

            Assertions.assertArrayEquals(expectedDigest, actualDigest);
        }
        assertTrue(true);
    }


    @Test
    void validateMandateTest() {
        //TO BE IMPLEMENTED
        Assertions.assertTrue(true);
    }
}