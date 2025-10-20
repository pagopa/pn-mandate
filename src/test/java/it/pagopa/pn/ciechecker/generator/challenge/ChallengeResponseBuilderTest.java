package it.pagopa.pn.ciechecker.generator.challenge;

import it.pagopa.pn.ciechecker.CieCheckerConstants;
import it.pagopa.pn.ciechecker.model.CieIas;
import it.pagopa.pn.ciechecker.model.CieValidationData;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.*;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;


public class ChallengeResponseBuilderTest {

    private static final Path privatekeyPathTest = Paths.get("src/test/resources/catest.key");
    private static final Path certificatoPathTest = Paths.get("src/test/resources/catest.pem");

    private CieValidationData validationData;
    private ChallengeResponseBuilder builder;
    private X509Certificate certX509;
    private PrivateKey privateKey;
    private PublicKey publicKey;

    @BeforeEach
    void setUp() throws Exception {
        Security.addProvider(new BouncyCastleProvider());

        byte[] certificatoByte = Files.readAllBytes(certificatoPathTest);
        byte[] privateKeyByte = Files.readAllBytes(privatekeyPathTest);

        certX509 = (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(certificatoByte));
        System.out.println("Certificato caricato: " + certX509.getSubjectDN());

        privateKey = parsePrivateKey(privateKeyByte);

        publicKey = certX509.getPublicKey();
        byte[] publicKey = certX509.getPublicKey().getEncoded();

        CieIas ias = new CieIas();
        ias.setPublicKey(publicKey);
        validationData = new CieValidationData();
        validationData.setNonce("02461");
        validationData.setCieIas(ias);
        builder = new ChallengeResponseBuilder(validationData);

    }

    @Test
    void generateSignedNonce() throws Exception {

        CieValidationData data = builder.generateSignedNonce(validationData.getNonce(), certX509, privateKey);

        String signedNonceBase64 = Base64.getEncoder().encodeToString(validationData.getSignedNonce());

        System.out.println("Nonce: " + validationData.getNonce());
        System.out.println("SignedNonce (Base64): " + signedNonceBase64);

        Assertions.assertTrue(verifySignedNonce(validationData.getNonce(), validationData.getSignedNonce(), publicKey));
    }


    private static boolean verifySignedNonce(String nonce, byte[] signedNonce, PublicKey publicKey) throws Exception {
        Signature signature = Signature.getInstance(CieCheckerConstants.SHA_1_WITH_RSA, "BC");
        signature.initVerify(publicKey);
        signature.update(nonce.getBytes(StandardCharsets.UTF_8));
        return signature.verify(signedNonce);
    }

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
