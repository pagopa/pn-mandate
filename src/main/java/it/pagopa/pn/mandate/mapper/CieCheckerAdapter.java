package it.pagopa.pn.mandate.mapper;

import it.pagopa.pn.ciechecker.exception.CieCheckerException;
import it.pagopa.pn.mandate.appio.generated.openapi.server.v1.dto.CIEValidationData;
import org.apache.commons.codec.DecoderException;

public interface CieCheckerAdapter {
    void validateMandate(CIEValidationData data,String nonce,String delegatorTaxId) throws CieCheckerException;
}
