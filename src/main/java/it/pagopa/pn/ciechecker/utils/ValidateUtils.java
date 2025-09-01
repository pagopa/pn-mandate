package it.pagopa.pn.ciechecker.utils;

import it.pagopa.pn.ciechecker.exception.CieCheckerException;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cms.*;
import org.bouncycastle.util.Store;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.*;
import java.util.*;
import java.util.stream.Collectors;

import static it.pagopa.pn.ciechecker.CieCheckerConstants.*;

public class ValidateUtils {

    private ValidateUtils() {}

    public static X509CertificateHolder extractDscCertDer(byte[] signedDataPkcs7) throws CMSException {
        CMSSignedData cms = new CMSSignedData(signedDataPkcs7);

        Store<X509CertificateHolder> certStore = cms.getCertificates();

        Collection<X509CertificateHolder> matches = certStore.getMatches(null);
        if (!matches.isEmpty()) {
            return matches.iterator().next();
        }

        throw new CieCheckerException(EXC_NO_CERT);
    }

    public static List<X509Certificate> parseCscaAnchors(Collection<byte[]> cscaAnchorBlobs,
                                                          CertificateFactory x509Cf) throws CertificateException {
        if (cscaAnchorBlobs == null || cscaAnchorBlobs.isEmpty()) {
            throw new CertificateException(NO_CSCA_ANCHORS_PROVIDED);
        }
        List<X509Certificate> out = new ArrayList<>();
        for (byte[] blob : cscaAnchorBlobs) {
            Collection<? extends Certificate> cs = x509Cf.generateCertificates(new ByteArrayInputStream(blob));
            for (Certificate c : cs) out.add((X509Certificate) c);
        }
        if (out.isEmpty()) throw new CertificateException(PARSED_ZERO_CSCA_CERTIFICATES);
        return out;
    }

    public static boolean verifyDscAgainstTrustBundle(byte[] dscDer, Collection<X509Certificate> cscaTrustAnchors, Date atTime) {
        try {
            if (Objects.isNull(dscDer) || Objects.isNull(cscaTrustAnchors)) return false;
            if (dscDer.length == 0) return false;

            CertificateFactory x509Cf = CertificateFactory.getInstance(X_509);

            X509Certificate dsc = (X509Certificate) x509Cf.generateCertificate(new ByteArrayInputStream(dscDer));
            CertPath path = x509Cf.generateCertPath(Collections.singletonList(dsc));

            Set<TrustAnchor> anchors = cscaTrustAnchors.stream().map(a -> new TrustAnchor(a, null)).collect(Collectors.toSet());

            PKIXParameters params = new PKIXParameters(anchors);
            params.setRevocationEnabled(false);
            if (atTime != null) params.setDate(atTime);

            CertPathValidator.getInstance(PKIX).validate(path, params); // No exception thrown = ok
            return true;

        } catch (CertificateException | InvalidAlgorithmParameterException | NoSuchAlgorithmException e) {
            throw new CieCheckerException(e);
        } catch (CertPathValidatorException e) {
            return false;
        }
    }

    public static boolean verifyDscAgainstAnchorBytes(byte[] dscDerOrPem,
                                               Collection<byte[]> cscaAnchorBlobs,
                                               Date atTime) {
        try {
            CertificateFactory x509Cf = CertificateFactory.getInstance(X_509);
            List<X509Certificate> anchors = parseCscaAnchors(cscaAnchorBlobs, x509Cf);
            return verifyDscAgainstTrustBundle(dscDerOrPem, anchors, atTime);
        } catch (CertificateException e) {
            return false;
        }
    }

    public static boolean verifyDscAgainstTrustBundle(X509CertificateHolder dscHolder,
                                                      Collection<X509Certificate> cscaTrustAnchors,
                                                      Date atTime) {
        try {
            if (dscHolder == null) return false;
            return verifyDscAgainstTrustBundle(dscHolder.getEncoded(), cscaTrustAnchors, atTime);
        } catch (IOException e) {
            throw new CieCheckerException(e);
        }
    }

}
