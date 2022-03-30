package it.pagopa.pn.mandate.middleware.microservice;


import java.net.ConnectException;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
 
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.TimeoutException;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient; 
import reactor.util.retry.Retry;

@Component
public class PnDataVaultClient {
    
    //private final InfoPaApi infoPaApi;

    public PnDataVaultClient(@Value("${pn.mandate.client.datavault.basepath}") String basepath ) {
        
        HttpClient httpClient = HttpClient.create().option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 1000)
            .doOnConnected(connection -> connection.addHandlerLast(new ReadTimeoutHandler(1000, TimeUnit.MILLISECONDS)));        
/*
        WebClient webClient = ApiClient.buildWebClientBuilder()        
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .build();
        ApiClient newApiClient = new ApiClient(webClient);        
        newApiClient.setBasePath(basepath); */
        //this.infoPaApi = new InfoPaApi(newApiClient);
    }

    public Mono<FakeKeyValue[]> getExternalInfos(String internaluserId) {
        FakeKeyValue[] data =new FakeKeyValue[3];
        data[0] = new FakeKeyValue("name","fabrizio");
        data[1] = new FakeKeyValue("surname","frizzi");
        data[2] = new FakeKeyValue("email","fabriziofrizzi@gmail.com");

        return Mono.just(data);
    } 

    public Mono<String> getInternaluserId(FakeKeyValue[] externalInfos) {
        return Mono.just(UUID.randomUUID().toString());
    } 
 
}
