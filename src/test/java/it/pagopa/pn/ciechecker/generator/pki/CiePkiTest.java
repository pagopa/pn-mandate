package it.pagopa.pn.ciechecker.generator.pki;

import it.pagopa.pn.ciechecker.generator.model.CaAndKey;
import it.pagopa.pn.ciechecker.generator.model.CertAndKey;
import it.pagopa.pn.ciechecker.generator.model.Issuer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import java.io.ByteArrayInputStream;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class CiePkiTest {


    static CiePki pki;

    @BeforeAll
    static void init() {
        pki = new CiePki();
    }

    @Test
    void createChainAndVerityTest() throws Exception {
        CaAndKey ca = pki.createDevTestCA("CN=Dev Test CA,O=PagoPA,L=RM,C=IT", 3072, 3650);
        ca.certificate().checkValidity();

        Issuer issuer = new Issuer(ca.certificate(), ca.keyPair().getPrivate());
        CertAndKey ds = pki.issueDocumentSigner("CN=DocumentSigner-DEV,O=PagoPA,L=RM,C=IT",
                issuer, 2048, 1825);
        ds.certificate().checkValidity();

        CertAndKey ias = pki.issueIasCertificate("CN=CIE-IAS,O=PagoPA,L=RM,C=IT",
                ds.certificate(), ds.keyPair().getPrivate(), 1095);
        ias.certificate().checkValidity();

        ds.certificate().verify(ca.certificate().getPublicKey());
        ias.certificate().verify(ds.certificate().getPublicKey());
    }

    @Test
    void exportToDerTest() throws Exception {
        CaAndKey ca = pki.createDevTestCA("CN=RoundTripCA,O=PagoPA,C=IT", 3072, 3650);

        byte[] der = pki.toDer(ca.certificate());
        byte[] pem = pki.toPem(ca.certificate());

        // DER → X509
        X509Certificate xDer = (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(der));

        // PEM → DER → X509
        String pemStr = new String(pem);
        String b64 = pemStr.replace("-----BEGIN CERTIFICATE-----", "")
                .replace("-----END CERTIFICATE-----", "")
                .replaceAll("\\s+", "");
        byte[] derFromPem = Base64.getDecoder().decode(b64);
        X509Certificate xPem = (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(derFromPem));

        assertEquals(ca.certificate().getSubjectX500Principal(), xDer.getSubjectX500Principal());
        assertEquals(ca.certificate().getSubjectX500Principal(), xPem.getSubjectX500Principal());
        assertArrayEquals(der, derFromPem, "DER originario e DER estratto dal PEM devono coincidere");
    }

    @Test
    void signNonceDecryptAndVerifyTest() throws Exception {
        CaAndKey ca = pki.createDevTestCA("CN=Dev CA,O=PagoPA,C=IT", 3072, 3650);
        Issuer issuer = new Issuer(ca.certificate(), ca.keyPair().getPrivate());
        CertAndKey ds = pki.issueDocumentSigner("CN=DS,O=PagoPA,C=IT", issuer, 2048, 1825);
        CertAndKey ias = pki.issueIasCertificate("CN=IAS,O=PagoPA,C=IT",
                ds.certificate(), ds.keyPair().getPrivate(), 1095);

        byte[] nonce = "hello-nonce".getBytes();
        byte[] signed = pki.signWithIasPrivate(nonce, ias.keyPair().getPrivate());

        Cipher c = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        c.init(Cipher.DECRYPT_MODE, ias.certificate().getPublicKey());
        byte[] plain = c.doFinal(signed);

        assertArrayEquals(nonce, plain);
    }

    @Test
    void certificatesValidityTest() throws NoSuchAlgorithmException {
        CaAndKey ca = pki.createDevTestCA("CN=ValidityCA,O=PagoPA,C=IT", 3072, 3650);
        Issuer issuer = new Issuer(ca.certificate(), ca.keyPair().getPrivate());
        CertAndKey ds = pki.issueDocumentSigner("CN=DS,O=PagoPA,C=IT", issuer, 2048, 1825);
        CertAndKey ias = pki.issueIasCertificate("CN=IAS,O=PagoPA,C=IT",
                ds.certificate(), ds.keyPair().getPrivate(), 1095);

        Instant now = Instant.now();
        assertTrue(ca.certificate().getNotBefore().toInstant().isBefore(now.plusSeconds(3600)));
        assertTrue(ds.certificate().getNotAfter().toInstant().isAfter(now.plusSeconds(24 * 3600)));
        assertTrue(ias.certificate().getNotAfter().toInstant().isAfter(now.plusSeconds(24 * 3600)));
    }

    @Test
    void wrongIssuerDoesNotVerifyDs() {
        CaAndKey ca1 = pki.createDevTestCA("CN=CA1,O=PagoPA,C=IT", 3072, 3650);
        assertTrue(ca1.certificate().getNotBefore().toInstant().isBefore(Instant.now()));
        assertTrue(ca1.certificate().getNotAfter().toInstant().isAfter(Instant.now()));
        Issuer issuer1 = new Issuer(ca1.certificate(), ca1.keyPair().getPrivate());
        CertAndKey ds = pki.issueDocumentSigner("CN=DS,O=PagoPA,C=IT", issuer1, 2048, 1825);

        CaAndKey ca2 = pki.createDevTestCA("CN=CA2,O=PagoPA,C=IT", 3072, 3650);
        assertThrows(Exception.class, () -> ds.certificate().verify(ca2.certificate().getPublicKey()));
    }
}
