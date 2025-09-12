package it.pagopa.pn.ciechecker;

import it.pagopa.pn.ciechecker.model.CieIas;
import it.pagopa.pn.ciechecker.model.CieValidationData;
import it.pagopa.pn.ciechecker.model.ResultCieChecker;
import org.bouncycastle.crypto.CryptoException;

import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.List;

public interface CieChecker {

    void init();
    ResultCieChecker validateMandate(CieValidationData data) throws Exception;
    boolean verifyChallengeFromSignature(byte[] signature, byte[] pubKey, byte[] nis) throws NoSuchAlgorithmException, InvalidKeySpecException, CryptoException;
    public boolean extractChallengeFromSignature(byte[] signature, byte[] pubKey,byte[] nis) throws NoSuchAlgorithmException, InvalidKeySpecException, CryptoException;
    public boolean verifySodPassiveAuthCie(CieIas cieIas);
    //public boolean verifyDigitalSignatureMrtd(CieMrtd cieMrtd);
    boolean verifyIntegrity(Path sodPath, List<Path> dgPaths);

    ResultCieChecker verifyDigitalSignature(byte[] sod, List<byte[]> cscaAnchors);
}
