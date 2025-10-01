package it.pagopa.pn.mandate.mapper;

import it.pagopa.pn.ciechecker.CieChecker;
import it.pagopa.pn.ciechecker.exception.CieCheckerException;
import it.pagopa.pn.ciechecker.model.CieValidationData;
import it.pagopa.pn.ciechecker.model.ResultCieChecker;
import it.pagopa.pn.commons.exceptions.PnInternalException;
import it.pagopa.pn.mandate.appio.generated.openapi.server.v1.dto.CIEValidationData;
import it.pagopa.pn.mandate.exceptions.PnMandateExceptionCodes;
import it.pagopa.pn.mandate.utils.CieResultAnalyzer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class CieCheckerAdapterImpl implements CieCheckerAdapter {

    private final CieChecker cieChecker;
    private final CieCheckerAdapterMapper cieCheckerAdapterMapper;
    private final CieResultAnalyzer cieResultAnalyzer;

    public CieCheckerAdapterImpl(CieCheckerAdapterMapper cieCheckerAdapterMapper, CieChecker cieChecker, CieResultAnalyzer cieResultAnalyzer) {
        this.cieCheckerAdapterMapper = cieCheckerAdapterMapper;
        this.cieResultAnalyzer = cieResultAnalyzer;
        this.cieChecker = cieChecker;
        cieChecker.init();
    }

    @Override
    public void validateMandate(CIEValidationData data, String nonce, String delegatorTaxId) throws CieCheckerException {
        log.info("Starting CIE Checker validation");
        try {
            CieValidationData cieValidationData = cieCheckerAdapterMapper.mapToLibDto(data, nonce, delegatorTaxId);
            ResultCieChecker resultCieChecker = cieChecker.validateMandate(cieValidationData);
            cieResultAnalyzer.analyzeResult(resultCieChecker);
        } catch (PnInternalException exception) {
            throw new PnInternalException("Validation Cie Fail", PnMandateExceptionCodes.ERROR_CODE_MANDATE_INTERNAL_SERVER_ERROR);
        }
        log.info("CIE Checker validation completed successfully");
    }
}

