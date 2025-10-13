package it.pagopa.pn.ciechecker.generator.model;

import java.security.KeyPair;
import java.security.cert.X509Certificate;

public record CertAndKey(
        X509Certificate certificate,
        KeyPair keyPair
) {}