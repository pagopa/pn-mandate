package it.pagopa.pn.ciechecker;

import it.pagopa.pn.ciechecker.model.CieIas;
import it.pagopa.pn.ciechecker.model.CieValidationData;
import org.bouncycastle.crypto.CryptoException;

import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

public interface CieChecker {

    void init();
    boolean validateMandate(CieValidationData data);
    public boolean extractChallengeFromSignature(byte[] signature, byte[] pubKey,byte[] nis) throws NoSuchAlgorithmException, InvalidKeySpecException, CryptoException;
    public boolean verificationSodCie(CieIas cieIas);
}
