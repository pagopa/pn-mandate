package it.pagopa.pn.mandate.middleware.msclient;

import it.pagopa.pn.commons.log.PnLogger;
import it.pagopa.pn.commons.pnclients.CommonBaseClient;
import it.pagopa.pn.mandate.config.PnMandateConfig;
import it.pagopa.pn.mandate.microservice.msclient.generated.extreg.selfcare.v1.ApiClient;
import it.pagopa.pn.mandate.microservice.msclient.generated.extreg.selfcare.v1.api.InfoPaApi;
import it.pagopa.pn.mandate.microservice.msclient.generated.extreg.selfcare.v1.dto.PaSummaryDto;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import javax.annotation.PostConstruct;
import java.util.List;

@Component
@lombok.CustomLog
public class PnInfoPaClient extends CommonBaseClient {

    private InfoPaApi infoPaApi;
    private final PnMandateConfig pnMandateConfig;

    public PnInfoPaClient(PnMandateConfig pnMandateConfig) {
        this.pnMandateConfig = pnMandateConfig;
    }

    @PostConstruct
    public void init() {
        ApiClient apiClient = new ApiClient(super.initWebClient(ApiClient.buildWebClientBuilder()));
        apiClient.setBasePath(pnMandateConfig.getClientExtregBasepath());
        infoPaApi = new InfoPaApi(apiClient);
    }

    public Flux<PaSummaryDto> getManyPa(List<String> paIds) {
        log.logInvokingExternalService(PnLogger.EXTERNAL_SERVICES.PN_EXTERNAL_REGISTRIES, "Retrieving PAs summary infos");
        return infoPaApi.getManyPa(paIds);
    }
}
