package it.pagopa.pn.ciechecker.generator.loader;

import it.pagopa.pn.ciechecker.generator.model.CertAndKey;
import it.pagopa.pn.mandate.config.PnMandateConfig;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMEncryptedKeyPair;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.pkcs.PKCS8EncryptedPrivateKeyInfo;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Slf4j
public class CertAndKeyLoader {

    S3Client s3;
    String bucket;
    String key;


    public CertAndKeyLoader() {
        this.s3 = S3Client.builder().build();
        this.bucket=System.getProperty("cie.generator.bucket");
        this.key=System.getProperty("cie.generator.file-key");
    }

    public CertAndKey loadCaAndKeyFromS3() throws IOException, GeneralSecurityException {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        X509Certificate certificate = null;
        PrivateKey privateKey = null;

        try (InputStream s3Input = s3.getObject(request);
             ZipInputStream zis = new ZipInputStream(s3Input)) {

            for (ZipEntry zipEntry = zis.getNextEntry(); zipEntry != null; zipEntry = zis.getNextEntry()) {
                if (zipEntry.isDirectory()) continue;

                String name = zipEntry.getName();
                byte[] entryBytes = readAll(zis);
                zis.closeEntry();

                if (name.endsWith(".pem")) {
                    certificate = parseCertificate(entryBytes);
                } else if (name.endsWith(".key")) {
                    privateKey = parsePrivateKey(entryBytes);
                }
            }
        }

        if (certificate == null) {
            throw new FileNotFoundException(".pem non trovato nello zip");
        }
        if (privateKey == null) {
            throw new FileNotFoundException(".key non trovato nello zip");
        }


        return new CertAndKey(certificate, new KeyPair(certificate.getPublicKey(), privateKey));
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(8192);
        byte[] buf = new byte[8192];
        int r;
        while ((r = in.read(buf)) != -1) bos.write(buf, 0, r);
        return bos.toByteArray();
    }

    private static X509Certificate parseCertificate(byte[] derOrPem) throws CertificateException, IOException {
        try {
            return (X509Certificate) CertificateFactory.getInstance("X.509")
                    .generateCertificate(new ByteArrayInputStream(derOrPem));
        } catch (CertificateException ignore) { /* non era DER */ }

        try (PEMParser pp = new PEMParser(
                new java.io.StringReader(new String(derOrPem, StandardCharsets.US_ASCII)))) {
            Object obj = pp.readObject();
            if (obj instanceof X509CertificateHolder holder) {
                return new JcaX509CertificateConverter()
                        .setProvider(new BouncyCastleProvider()).getCertificate(holder);
            }
        }
        throw new CertificateException("Formato certificato non riconosciuto (attesi DER o PEM).");
    }

    private static PrivateKey parsePrivateKey(byte[] derOrPem) throws GeneralSecurityException, IOException {
        try {
            return KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(derOrPem));
        } catch (Exception ignore) { /* non era PKCS#8 DER */ }

        try (PEMParser pp = new PEMParser(
                new java.io.StringReader(new String(derOrPem, StandardCharsets.US_ASCII)))) {

            Object obj = pp.readObject();
            var conv = new JcaPEMKeyConverter().setProvider(new BouncyCastleProvider());

            if (obj instanceof PrivateKeyInfo pki) {
                return conv.getPrivateKey(pki);
            } else if (obj instanceof PEMKeyPair kp) {
                return conv.getKeyPair(kp).getPrivate();
            } else if (obj instanceof PEMEncryptedKeyPair
                    || obj instanceof PKCS8EncryptedPrivateKeyInfo) {
                throw new InvalidKeyException("Chiave privata cifrata: serve password e decrypt esplicito.");
            }
        }

        throw new InvalidKeyException("Formato chiave non supportato (attesi PKCS#8 DER/PEM o PKCS#1 PEM non cifrati).");
    }


}
