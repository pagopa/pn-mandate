package it.pagopa.pn.ciechecker.utils;

import it.pagopa.pn.ciechecker.exception.CieCheckerException;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cms.*;
import org.bouncycastle.util.Store;

import java.util.Collection;

public class ValidateUtils {

    private ValidateUtils() {}

    public static X509CertificateHolder extractDscCertDer(byte[] signedDataPkcs7) throws CMSException {
        CMSSignedData cms = new CMSSignedData(signedDataPkcs7);

        Store<X509CertificateHolder> certStore = cms.getCertificates();

        Collection<X509CertificateHolder> matches = certStore.getMatches(null);
        if (!matches.isEmpty()) {
            return matches.iterator().next();
        }

        throw new CieCheckerException("No certificates found in PKCS7");
    }

}
