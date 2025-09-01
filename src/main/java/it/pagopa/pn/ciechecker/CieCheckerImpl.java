package it.pagopa.pn.ciechecker;

import it.pagopa.pn.ciechecker.model.CieValidationData;

public class CieCheckerImpl implements CieChecker {

    @Override
    public void init() {
    }

    @Override
    public boolean validateMandate(CieValidationData data) {
        return false;
    }

    @Override
    public boolean extractChallengeFromSignature(byte[] signature, byte[] pubKey, byte[] nis) {
        return false;
    }


}
