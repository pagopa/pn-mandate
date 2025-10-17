package it.pagopa.pn.ciechecker.generator.challenge;

import it.pagopa.pn.ciechecker.CieChecker;
import it.pagopa.pn.ciechecker.CieCheckerConstants;
import it.pagopa.pn.ciechecker.CieCheckerInterface;
import it.pagopa.pn.ciechecker.model.CieIas;
import it.pagopa.pn.ciechecker.model.CieValidationData;
import it.pagopa.pn.ciechecker.model.ResultCieChecker;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.DecoderException;
import org.bouncycastle.asn1.pkcs.RSAPrivateKey;
import org.bouncycastle.asn1.pkcs.RSAPublicKey;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.crypto.encodings.PKCS1Encoding;
import org.bouncycastle.crypto.engines.RSAEngine;
import org.bouncycastle.crypto.params.RSAKeyParameters;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.Base64Utils;
import org.testcontainers.shaded.org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.*;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.util.Arrays;


public class ChallengeResponseBuilderTest {

    private static final Path basePath= Path.of("src","test","resources");
    private static final Path privatekeyPathTest = Paths.get("src/test/resources/catest.key");
    private static final Path certificatoPathTest = Paths.get("src/test/resources/catest.pem");

    private CieValidationData validationData;
    private ChallengeResponseBuilder builder;
    private X509Certificate certX509;
    private PrivateKey privateKey;


    @BeforeEach
    void setUp() throws Exception {

        byte[] certificatoByte = Files.readAllBytes(certificatoPathTest);
        byte[] privateKeyByte = Files.readAllBytes(privatekeyPathTest);

        certX509 = (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(certificatoByte));
        privateKey = parsePrivateKey(privateKeyByte);

        byte[] publicKey = certX509.getPublicKey().getEncoded();

        CieIas ias = new CieIas();
        ias.setPublicKey(publicKey);
        validationData = new CieValidationData();
        validationData.setNonce("02461");
        validationData.setCieIas(ias);
        builder = new ChallengeResponseBuilder(validationData);

    }


//    @Test
//    void verifyChallengeResponseTest() throws Exception {
//
//        builder.setSignedNonce(validationData.getNonce(), certX509, privateKey);
//
//        PublicKey publicKey = certX509.getPublicKey();
//        SubjectPublicKeyInfo spki = SubjectPublicKeyInfo.getInstance(publicKey.getEncoded());
//        RSAPublicKey pkcs1PublicKey = RSAPublicKey.getInstance(spki.parsePublicKey());
//        BigInteger modulus = pkcs1PublicKey.getModulus();
//        BigInteger publicExponent = pkcs1PublicKey.getPublicExponent();
//        RSAKeyParameters publicKeyParam = new RSAKeyParameters(false, modulus, publicExponent);
//
//        RSAEngine engine = new RSAEngine();
//        PKCS1Encoding engine2 = new PKCS1Encoding(engine);
//        engine2.init(false, publicKeyParam);
//
//        byte[] recovered = engine2.processBlock(validationData.getSignedNonce(), 0, validationData.getSignedNonce().length);
//        System.out.println("recovered: " + recovered);
//        System.out.println("nonce: " + validationData.getNonce().getBytes(StandardCharsets.UTF_8));
//
//        Assertions.assertTrue(Arrays.equals(recovered, validationData.getNonce().getBytes(StandardCharsets.UTF_8)));
//    }


    private static PrivateKey parsePrivateKey(byte[] derOrPem) throws GeneralSecurityException, IOException {
        try {
            return KeyFactory.getInstance("RSA")
                    .generatePrivate(new java.security.spec.PKCS8EncodedKeySpec(derOrPem));
        } catch (Exception ignore) { /* non era PKCS#8 DER */ }

        try (org.bouncycastle.openssl.PEMParser pp = new org.bouncycastle.openssl.PEMParser(
                new java.io.StringReader(new String(derOrPem, java.nio.charset.StandardCharsets.US_ASCII)))) {

            Object obj = pp.readObject();
            var conv = new org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter().setProvider(new BouncyCastleProvider());

            if (obj instanceof org.bouncycastle.asn1.pkcs.PrivateKeyInfo pki) {
                return conv.getPrivateKey(pki);
            } else if (obj instanceof org.bouncycastle.openssl.PEMKeyPair kp) {
                return conv.getKeyPair(kp).getPrivate();
            } else if (obj instanceof org.bouncycastle.openssl.PEMEncryptedKeyPair
                    || obj instanceof org.bouncycastle.pkcs.PKCS8EncryptedPrivateKeyInfo) {
                throw new InvalidKeyException("Chiave privata cifrata: serve password e decrypt esplicito.");
            }
        }

        throw new InvalidKeyException("Formato chiave non supportato (attesi PKCS#8 DER/PEM o PKCS#1 PEM non cifrati).");
    }



}
