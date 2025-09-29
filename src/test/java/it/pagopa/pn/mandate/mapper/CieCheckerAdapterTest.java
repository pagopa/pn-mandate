package it.pagopa.pn.mandate.mapper;

import it.pagopa.pn.ciechecker.CieChecker;
import it.pagopa.pn.ciechecker.exception.CieCheckerException;
import it.pagopa.pn.ciechecker.model.CieValidationData;
import it.pagopa.pn.ciechecker.model.ResultCieChecker;
import it.pagopa.pn.commons.exceptions.PnInternalException;
import it.pagopa.pn.mandate.appio.generated.openapi.server.v1.dto.CIEValidationData;
import it.pagopa.pn.mandate.exceptions.PnMandateExceptionCodes;
import it.pagopa.pn.mandate.utils.CieResultAnalyzer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CieCheckerAdapterTest {

    private CieCheckerAdapterMapper cieCheckerAdapterMapper;
    private CieCheckerAdapterImpl cieCheckerAdapter;

    @BeforeEach
    void setUp() {
        cieCheckerAdapterMapper = mock(CieCheckerAdapterMapper.class);
        CieResultAnalyzer cieResultAnalyzer = mock(CieResultAnalyzer.class);
        cieCheckerAdapter = new CieCheckerAdapterImpl(cieCheckerAdapterMapper, cieResultAnalyzer);
    }

    @Test
    void validateMandate_success() throws CieCheckerException {
        CIEValidationData data = mock(CIEValidationData.class);
        String nonce = "nonce";
        String delegatorTaxId = "taxId";
        CieValidationData cieValidationData = mock(CieValidationData.class);

        when(cieCheckerAdapterMapper.mapToLibDto(data, nonce)).thenReturn(cieValidationData);

        CieChecker cieCheckerMock = mock(CieChecker.class);
        doNothing().when(cieCheckerMock).init();
        when(cieCheckerMock.validateMandate(cieValidationData)).thenReturn(ResultCieChecker.OK);

        try {
            var field = cieCheckerAdapter.getClass().getDeclaredField("cieChecker");
            field.setAccessible(true);
            field.set(cieCheckerAdapter, cieCheckerMock);
        } catch (Exception e) {
            fail("Reflection error: " + e.getMessage());
        }

        cieCheckerAdapter.validateMandate(data, nonce, delegatorTaxId);

        verify(cieCheckerMock).init();
        verify(cieCheckerAdapterMapper).mapToLibDto(data, nonce);
        verify(cieCheckerMock).validateMandate(cieValidationData);
    }


    @Test
    void validateMandate_internalException() throws CieCheckerException {
        CIEValidationData data = mock(CIEValidationData.class);
        String nonce = "nonce";
        String delegatorTaxId = "taxId";

        CieChecker cieCheckerMock = mock(CieChecker.class);
        doNothing().when(cieCheckerMock).init();
        try {
            var field = cieCheckerAdapter.getClass().getDeclaredField("cieChecker");
            field.setAccessible(true);
            field.set(cieCheckerAdapter, cieCheckerMock);
        } catch (Exception e) {
            fail("Reflection error: " + e.getMessage());
        }

        when(cieCheckerAdapterMapper.mapToLibDto(any(), any()))
                .thenThrow(new PnInternalException("fail", PnMandateExceptionCodes.ERROR_CODE_MANDATE_INTERNAL_SERVER_ERROR));

        PnInternalException ex = assertThrows(PnInternalException.class, () ->
                cieCheckerAdapter.validateMandate(data, nonce, delegatorTaxId)
        );
        assertEquals("Internal Server Error", ex.getMessage());
    }

}

