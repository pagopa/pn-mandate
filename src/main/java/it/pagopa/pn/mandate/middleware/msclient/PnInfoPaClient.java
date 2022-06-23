package it.pagopa.pn.mandate.middleware.msclient;


import io.netty.handler.timeout.TimeoutException;
import it.pagopa.pn.mandate.config.PnMandateConfig;
import it.pagopa.pn.mandate.microservice.msclient.generated.infopa.v1.ApiClient;
import it.pagopa.pn.mandate.microservice.msclient.generated.infopa.v1.api.InfoPaApi;
import it.pagopa.pn.mandate.microservice.msclient.generated.infopa.v1.dto.PaInfoDto;
import it.pagopa.pn.mandate.middleware.msclient.common.BaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import javax.annotation.PostConstruct;
import java.net.ConnectException;
import java.time.Duration;

@Component
public class PnInfoPaClient extends BaseClient {

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

    public Mono<PaInfoDto> getOnePa(String id) {

        return this.infoPaApi                        
                .getOnePa(id)                
                .retryWhen(
                    Retry.backoff(2, Duration.ofMillis(25))
                            .filter(throwable -> throwable instanceof TimeoutException || throwable instanceof ConnectException)
                );
    } 
}
