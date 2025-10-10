package it.pagopa.pn.ciechecker.generator.loader;

import it.pagopa.pn.ciechecker.client.s3.S3BucketClient;
import it.pagopa.pn.ciechecker.utils.ValidateUtils;
import it.pagopa.pn.mandate.config.PnMandateConfig;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static it.pagopa.pn.ciechecker.CieCheckerConstants.*;

@Slf4j

public class AnchorLoader {

    PnMandateConfig pnMandateConfig;
    S3BucketClient s3;

    public AnchorLoader(PnMandateConfig pnMandateConfig, S3BucketClient s3) {
        this.pnMandateConfig = pnMandateConfig;
        this.s3 = s3;
    }

    public List<X509Certificate> loadFromS3(String path) throws IOException {
        try (InputStream in = s3.getObjectContent(path)) {
            List<X509Certificate> certificates = new ArrayList<>();
            for (X509Certificate cert : ValidateUtils.getX509CertListFromZipFile(in)){
                certificates.add(cert);
            }
            if (certificates.isEmpty()) log.warn("certificate list is empty");
            return certificates;
        }
    }

}
