package it.pagopa.pn.ciechecker.generator.loader;

import it.pagopa.pn.ciechecker.generator.model.CertAndKey;
import it.pagopa.pn.mandate.config.PnMandateConfig;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import java.io.*;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static it.pagopa.pn.ciechecker.utils.ValidateUtils.*;

@Slf4j
public class CertAndKeyLoader {

    PnMandateConfig pnMandateConfig;
    S3Client s3;
    String bucket;
    String key;


    public CertAndKeyLoader() {
        this.s3 = S3Client.builder().build();
        this.bucket=System.getProperty("cie.generator.bucket");
        this.key=System.getProperty("cie.generator.file-key");
    }

    public CertAndKey loadIssuerCertAndKeyFromS3() throws IOException, GeneralSecurityException {
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
            throw new FileNotFoundException("catest.pem non trovato nello zip " + pnMandateConfig.getGeneratorZipName());
        }
        if (privateKey == null) {
            throw new FileNotFoundException("catest.key non trovato nello zip " + pnMandateConfig.getGeneratorZipName());
        }


        return new CertAndKey(certificate, new KeyPair(certificate.getPublicKey(), privateKey));
    }



}
