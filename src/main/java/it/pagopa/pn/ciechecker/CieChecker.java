package it.pagopa.pn.ciechecker;

import it.pagopa.pn.ciechecker.exception.CieCheckerException;
import it.pagopa.pn.ciechecker.model.CieIas;
import it.pagopa.pn.ciechecker.model.CieMrtd;
import it.pagopa.pn.ciechecker.model.CieValidationData;
import it.pagopa.pn.ciechecker.model.ResultCieChecker;

import java.util.List;

public interface CieChecker {

    void init();
    ResultCieChecker validateMandate(CieValidationData data) throws CieCheckerException;
    ResultCieChecker verifyChallengeFromSignature(CieValidationData data);
    //public boolean extractChallengeFromSignature(byte[] signature, byte[] pubKey,byte[] nis) throws NoSuchAlgorithmException, InvalidKeySpecException, CryptoException;
    public boolean verifySodPassiveAuthCie(CieIas cieIas);
    //public boolean verifyDigitalSignatureMrtd(CieMrtd cieMrtd);
    ResultCieChecker verifyIntegrity(CieMrtd cieMrtd);

    ResultCieChecker verifyDigitalSignature(byte[] sod, List<byte[]> cscaAnchors);
}
