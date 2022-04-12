package it.pagopa.pn.mandate.middleware.microservice;


import java.net.ConnectException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
 
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.TimeoutException;
import it.pagopa.pn.mandate.microservice.client.datavault.v1.ApiClient;
import it.pagopa.pn.mandate.microservice.client.datavault.v1.api.MandatesApi;
import it.pagopa.pn.mandate.microservice.client.datavault.v1.api.RecipientsApi;
import it.pagopa.pn.mandate.microservice.client.datavault.v1.dto.AddressAndDenominationDtoDto;
import it.pagopa.pn.mandate.microservice.client.datavault.v1.dto.BaseRecipientDtoDto;
import it.pagopa.pn.mandate.microservice.client.datavault.v1.dto.MandateDtoDto;
import it.pagopa.pn.mandate.microservice.client.datavault.v1.dto.RecipientTypeDto;
import it.pagopa.pn.mandate.microservice.client.datavault.v1.dto.AddressAndDenominationDtoDto.KindEnum;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient; 
import reactor.util.retry.Retry;

/**
 * Classe wrapper di pn-data-vault, con gestione del backoff
 */
@Component
public class PnDataVaultClient {
    
    private final RecipientsApi recipientsApi;
    private final MandatesApi mandatesApi;

    public PnDataVaultClient(@Value("${pn.mandate.client.datavault.basepath}") String basepath ) {
        
        HttpClient httpClient = HttpClient.create().option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 1000)
            .doOnConnected(connection -> connection.addHandlerLast(new ReadTimeoutHandler(1000, TimeUnit.MILLISECONDS)));        

        WebClient webClient = ApiClient.buildWebClientBuilder()        
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .build();
        ApiClient newApiClient = new ApiClient(webClient);        
        newApiClient.setBasePath(basepath); 
        this.recipientsApi = new RecipientsApi(newApiClient);

        httpClient = HttpClient.create().option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 1000)
        .doOnConnected(connection -> connection.addHandlerLast(new ReadTimeoutHandler(1000, TimeUnit.MILLISECONDS)));        

        webClient = ApiClient.buildWebClientBuilder()        
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .build();
        newApiClient = new ApiClient(webClient);        
        newApiClient.setBasePath(basepath); 
        this.mandatesApi = new MandatesApi(newApiClient);
    }

    /**
     * Ritorna una lista di nominativi in base alla lista di iuid passati
     *
     * @param internalIds lista di iuid
     * @return lista di nominativi
     */
    public Flux<BaseRecipientDtoDto> getRecipientDenominationByInternalId(List<String> internalIds)
    {
        return recipientsApi.getRecipientDenominationByInternalId(internalIds)
            .retryWhen(
                    Retry.backoff(2, Duration.ofMillis(25))
                            .filter(throwable -> throwable instanceof TimeoutException || throwable instanceof ConnectException)
                );                             
            
    }

    /**
     * Genera (o recupera) un internaluserId in base al CF/PIVA
     * @param isPerson true per PF, false per PG
     * @param fiscalCode CF o PIVA
     * @return iuid
     */
    public Mono<String> ensureRecipientByExternalId(boolean isPerson, String fiscalCode)
    {
        return recipientsApi.ensureRecipientByExternalId(isPerson?RecipientTypeDto.PF:RecipientTypeDto.PG, fiscalCode)
            .retryWhen(
                    Retry.backoff(2, Duration.ofMillis(25))
                            .filter(throwable -> throwable instanceof TimeoutException || throwable instanceof ConnectException)
                );
            
    }

    /**
     * Salva per una certa delega, le info riguardanti il DELEGATO
     *
     * @param mandateId id della delega
     * @param name nome
     * @param surname cognome
     * @param email email
     * @param businessName ragione sociale
     * @return void
     */
    public Mono<String> updateMandateById(String mandateId, String name, String surname, String email, String businessName)
    {
        AddressAndDenominationDtoDto addressdto = new AddressAndDenominationDtoDto();
        addressdto.setKind(KindEnum.EMAIL);
        addressdto.setDestName(name);
        addressdto.setDestSurname(surname);
        addressdto.setValue(email);
        addressdto.setDestBusinessName(businessName);
        return mandatesApi.updateMandateById(mandateId, addressdto)
            .retryWhen(
                    Retry.backoff(2, Duration.ofMillis(25))
                            .filter(throwable -> throwable instanceof TimeoutException || throwable instanceof ConnectException)
                )
                .then(Mono.just("OK"));

    }

    /**
     * Ritorna le info sui DELEGATI in base agli id delle deleghe passati
     * @param mandateIds lista di id deleghe
     * @return lista userinfo deleghe
     */
    public Flux<MandateDtoDto> getMandatesByIds(List<String> mandateIds)
    {                
        return mandatesApi.getMandatesByIds(mandateIds)
            .retryWhen(
                    Retry.backoff(2, Duration.ofMillis(25))
                            .filter(throwable -> throwable instanceof TimeoutException || throwable instanceof ConnectException)
                );
    }
}
