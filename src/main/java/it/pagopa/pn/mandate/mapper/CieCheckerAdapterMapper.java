package it.pagopa.pn.mandate.mapper;

import it.pagopa.pn.ciechecker.model.CieIas;
import it.pagopa.pn.ciechecker.model.CieMrtd;
import it.pagopa.pn.ciechecker.model.CieValidationData;
import it.pagopa.pn.mandate.appio.generated.openapi.server.v1.dto.CIEValidationData;
import it.pagopa.pn.mandate.appio.generated.openapi.server.v1.dto.MRTDData;
import it.pagopa.pn.mandate.appio.generated.openapi.server.v1.dto.NISData;
import org.apache.commons.codec.binary.Hex;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class CieCheckerAdapterMapper implements CieCheckerAdapterMapperInterface{
    @Override
    public CieValidationData mapToLibDto(CIEValidationData cieValidationDataInput, String nonce, String delegatorTaxId) {
        CieValidationData cieValidationDataLib = new CieValidationData();
        cieValidationDataLib.setCieIas(createIas(cieValidationDataInput.getNisData()));
        cieValidationDataLib.setCieMrtd(createMrtd(cieValidationDataInput.getMrtdData()));
        cieValidationDataLib.setSignedNonce(cieValidationDataInput.getSignedNonce().getBytes(StandardCharsets.UTF_8));
        cieValidationDataLib.setNonce(Hex.encodeHexString(nonce.getBytes(StandardCharsets.UTF_8)));
        cieValidationDataLib.setCodFiscDelegante(delegatorTaxId);
        return cieValidationDataLib;
    }

    private CieIas createIas (NISData nisData){
        CieIas cieIas = new CieIas();
        cieIas.setNis(nisData.getNis().getBytes(StandardCharsets.UTF_8));
        cieIas.setSod(nisData.getSod().getBytes(StandardCharsets.UTF_8));
        cieIas.setPublicKey(nisData.getPubKey().getBytes(StandardCharsets.UTF_8));
        return cieIas;
    }

    private CieMrtd createMrtd (MRTDData mrtdData){
        CieMrtd cieMrtd = new CieMrtd();
        cieMrtd.setSod(mrtdData.getSod().getBytes(StandardCharsets.UTF_8));
        cieMrtd.setDg1(mrtdData.getDg1().getBytes(StandardCharsets.UTF_8));
        cieMrtd.setDg11(mrtdData.getDg11().getBytes(StandardCharsets.UTF_8));
        return cieMrtd;
    }
}
