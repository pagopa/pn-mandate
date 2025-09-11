package it.pagopa.pn.mandate.validation;

import it.pagopa.pn.ciechecker.CieChecker;
import it.pagopa.pn.ciechecker.CieCheckerImpl;
import it.pagopa.pn.ciechecker.utils.ValidateUtils;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.bouncycastle.crypto.CryptoException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPublicKeySpec;

class CieCheckerTest {

    private static CieChecker cieChecker;
    private static final Path basePath= Path.of("src","test","resources");

    @BeforeAll
    static void setUp() {
        cieChecker = new CieCheckerImpl();
        cieChecker.init();
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
    void validateMandateTest() {
        //TO BE IMPLEMENTED
        Assertions.assertTrue(true);
    }
}