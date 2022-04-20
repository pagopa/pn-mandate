package it.pagopa.pn.mandate.middleware.microservice;


import java.net.ConnectException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
 
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.TimeoutException;
import it.pagopa.pn.mandate.microservice.client.infopa.v1.ApiClient;
import it.pagopa.pn.mandate.microservice.client.infopa.v1.api.InfoPaApi;
import it.pagopa.pn.mandate.microservice.client.infopa.v1.dto.PaInfoDto;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient; 
import reactor.util.retry.Retry;

@Component
public class PnInfoPaClient {

    private final InfoPaApi infoPaApi;

    public PnInfoPaClient(@Value("${pn.mandate.client.infopa.basepath}") String basepath ) {
        
        HttpClient httpClient = HttpClient.create().option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 1000)
            .doOnConnected(connection -> connection.addHandlerLast(new ReadTimeoutHandler(1000, TimeUnit.MILLISECONDS)));        

        WebClient webClient = ApiClient.buildWebClientBuilder()        
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .build();
        ApiClient newApiClient = new ApiClient(webClient);        
        newApiClient.setBasePath(basepath); 
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
