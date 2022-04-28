package it.pagopa.pn.mandate.middleware.msclient;


import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.TimeoutException;
import it.pagopa.pn.mandate.config.PnMandateConfig;
import it.pagopa.pn.mandate.microservice.msclient.generated.infopa.v1.ApiClient;
import it.pagopa.pn.mandate.microservice.msclient.generated.infopa.v1.api.InfoPaApi;
import it.pagopa.pn.mandate.microservice.msclient.generated.infopa.v1.dto.PaInfoDto;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;

import javax.annotation.PostConstruct;
import java.net.ConnectException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Component
public class PnInfoPaClient {

    private InfoPaApi infoPaApi;
    private final PnMandateConfig pnMandateConfig;

    public PnInfoPaClient(PnMandateConfig pnMandateConfig) {
        this.pnMandateConfig = pnMandateConfig;
    }

    @PostConstruct
    public void init(){
        HttpClient httpClient = HttpClient.create().option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                .doOnConnected(connection -> connection.addHandlerLast(new ReadTimeoutHandler(10000, TimeUnit.MILLISECONDS)));

        WebClient webClient = ApiClient.buildWebClientBuilder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
        ApiClient newApiClient = new ApiClient(webClient);
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
