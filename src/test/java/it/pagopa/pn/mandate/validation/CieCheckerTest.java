package it.pagopa.pn.mandate.validation;

import it.pagopa.pn.ciechecker.CieChecker;
import it.pagopa.pn.ciechecker.CieCheckerImpl;
import it.pagopa.pn.ciechecker.model.CieMrtd;
import it.pagopa.pn.ciechecker.model.ResultCieChecker;
import it.pagopa.pn.ciechecker.model.CieIas;
import it.pagopa.pn.ciechecker.model.CieValidationData;
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
import java.util.*;

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

        assertEquals(ResultCieChecker.KO_NOTFOUND_DIGEST_SOD, result);
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

        assertEquals(ResultCieChecker.KO_NOTFOUND_MRTD_SOD, result);
    }

    @Test
    void testVerifyIntegrityFailDG1() throws Exception {
        byte[] sod = Files.readAllBytes(sodFile);

        // DG1 corrotto (modifica 1 byte)
        byte[] dg1 = Files.readAllBytes(dg1FilesCorrupted);

        CieMrtd mrtd = new CieMrtd();
        mrtd.setSod(sod);
        mrtd.setDg1(dg1);
        mrtd.setDg11(null);

        ResultCieChecker result = cieChecker.verifyIntegrity(mrtd);
        assertEquals(ResultCieChecker.KO_NOT_SAME_DIGEST, result, "DG1 corrotto deve dare KO");
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
        assertEquals(ResultCieChecker.KO_NOT_SAME_DIGEST, result, "DG11 corrotto deve dare KO");
    }

    @Test
    void validateMandateTest() {
        //TO BE IMPLEMENTED
        Assertions.assertTrue(true);
    }

}