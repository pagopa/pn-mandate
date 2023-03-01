package it.pagopa.pn.mandate.middleware.msclient;


import it.pagopa.pn.commons.pnclients.CommonBaseClient;
import it.pagopa.pn.mandate.config.PnMandateConfig;
import it.pagopa.pn.mandate.microservice.msclient.generated.infopa.v1.ApiClient;
import it.pagopa.pn.mandate.microservice.msclient.generated.infopa.v1.api.InfoPaApi;
import it.pagopa.pn.mandate.microservice.msclient.generated.infopa.v1.dto.PaSummaryDto;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import javax.annotation.PostConstruct;
import java.util.List;

@Component
public class PnInfoPaClient extends CommonBaseClient {

    private InfoPaApi infoPaApi;
    private final PnMandateConfig pnMandateConfig;

    public PnInfoPaClient(PnMandateConfig pnMandateConfig) {
        this.pnMandateConfig = pnMandateConfig;
    }

    @PostConstruct
    public void init(){

        ApiClient newApiClient = new ApiClient(super.initWebClient(ApiClient.buildWebClientBuilder()));
        newApiClient.setBasePath(pnMandateConfig.getClientInfopaBasepath());
        this.infoPaApi = new InfoPaApi(newApiClient);
    }

    public Flux<PaSummaryDto> getManyPa(List<String> paIds) {
        return this.infoPaApi.getManyPa(paIds);
    }
}
