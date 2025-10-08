package it.pagopa.pn.mandate.mapper;

import it.pagopa.pn.ciechecker.model.CieIas;
import it.pagopa.pn.ciechecker.model.CieMrtd;
import it.pagopa.pn.ciechecker.model.CieValidationData;
import it.pagopa.pn.mandate.appio.generated.openapi.server.v1.dto.CIEValidationData;
import it.pagopa.pn.mandate.appio.generated.openapi.server.v1.dto.MRTDData;
import it.pagopa.pn.mandate.appio.generated.openapi.server.v1.dto.NISData;
import org.springframework.stereotype.Component;

import java.util.Base64;

@Component
public class CieCheckerAdapterMapper implements CieCheckerAdapterMapperInterface{
    @Override
    public CieValidationData mapToLibDto(CIEValidationData cieValidationDataInput, String nonce, String delegatorTaxId) {
        CieValidationData cieValidationDataLib = new CieValidationData();
        cieValidationDataLib.setCieIas(mapIas(cieValidationDataInput.getNisData()));
        cieValidationDataLib.setCieMrtd(mapMrtd(cieValidationDataInput.getMrtdData()));
        cieValidationDataLib.setSignedNonce(Base64.getDecoder().decode(cieValidationDataInput.getSignedNonce()));
        cieValidationDataLib.setNonce(nonce);
        cieValidationDataLib.setCodFiscDelegante(delegatorTaxId);
        return cieValidationDataLib;
    }

    private CieIas mapIas(NISData nisData) {
        CieIas cieIas = new CieIas();
        cieIas.setNis(Base64.getDecoder().decode(nisData.getNis()));
        cieIas.setSod(Base64.getDecoder().decode(nisData.getSod()));
        cieIas.setPublicKey(Base64.getDecoder().decode(nisData.getPubKey()));
        return cieIas;
    }

    private CieMrtd mapMrtd(MRTDData mrtdData) {
        CieMrtd cieMrtd = new CieMrtd();
        cieMrtd.setSod(Base64.getDecoder().decode(mrtdData.getSod()));
        cieMrtd.setDg1(Base64.getDecoder().decode(mrtdData.getDg1()));
        cieMrtd.setDg11(Base64.getDecoder().decode(mrtdData.getDg11()));
        return cieMrtd;
    }
}
