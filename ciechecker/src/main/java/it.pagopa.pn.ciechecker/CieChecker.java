package it.pagopa.pn.ciechecker;

import it.pagopa.pn.ciechecker.model.CieValidationData;

public interface CieChecker {

    void init();
    boolean validateMandate(CieValidationData data);

}