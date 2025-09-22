package it.pagopa.pn.ciechecker;

import it.pagopa.pn.ciechecker.exception.CieCheckerException;
import it.pagopa.pn.ciechecker.model.CieIas;
import it.pagopa.pn.ciechecker.model.CieMrtd;
import it.pagopa.pn.ciechecker.model.CieValidationData;
import it.pagopa.pn.ciechecker.model.ResultCieChecker;
import org.bouncycastle.cms.CMSSignedData;

import java.security.cert.X509Certificate;
import java.util.List;

public interface CieChecker {

    void init();
    ResultCieChecker validateMandate(CieValidationData data) throws CieCheckerException;
    ResultCieChecker verifyChallengeFromSignature(CieValidationData data) throws CieCheckerException;

    boolean verifySodPassiveAuthCie(CMSSignedData cms, byte[] cieIasNis) throws CieCheckerException;

    ResultCieChecker verifyIntegrity(CieMrtd cieMrtd) throws CieCheckerException;

    ResultCieChecker verifyDigitalSignature(CMSSignedData cms) throws CieCheckerException;

    void setCscaAnchor(List<X509Certificate> cscaAnchor);
    List<X509Certificate> getCscaAnchor();

    List<X509Certificate> extractCscaAnchor() throws CieCheckerException;
}
