package it.pagopa.pn.mandate.middleware.msclient;

import it.pagopa.pn.commons.pnclients.CommonBaseClient;
import it.pagopa.pn.mandate.config.PnMandateConfig;
import it.pagopa.pn.mandate.microservice.msclient.generated.datavault.v1.ApiClient;
import it.pagopa.pn.mandate.microservice.msclient.generated.datavault.v1.api.MandatesApi;
import it.pagopa.pn.mandate.microservice.msclient.generated.datavault.v1.api.RecipientsApi;
import it.pagopa.pn.mandate.microservice.msclient.generated.datavault.v1.dto.*;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;
import java.util.List;

/**
 * Classe wrapper di pn-data-vault, con gestione del backoff
 */
@Component
public class PnDataVaultClient extends CommonBaseClient {
    
    private RecipientsApi recipientsApi;
    private MandatesApi mandatesApi;
    private final PnMandateConfig pnMandateConfig;

    public PnDataVaultClient(PnMandateConfig pnMandateConfig) {
        this.pnMandateConfig = pnMandateConfig;
    }

    @PostConstruct
    public void init(){

        ApiClient newApiClient = new ApiClient(super.initWebClient(ApiClient.buildWebClientBuilder()));
        newApiClient.setBasePath(pnMandateConfig.getClientDatavaultBasepath());
        this.recipientsApi = new RecipientsApi(newApiClient);

        newApiClient = new ApiClient(super.initWebClient(ApiClient.buildWebClientBuilder()));
        newApiClient.setBasePath(pnMandateConfig.getClientDatavaultBasepath());
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
        return recipientsApi.getRecipientDenominationByInternalId(internalIds);
    }

    /**
     * Genera (o recupera) un internaluserId in base al CF/PIVA
     * @param isPerson true per PF, false per PG
     * @param fiscalCode CF o PIVA
     * @return iuid
     */
    public Mono<String> ensureRecipientByExternalId(boolean isPerson, String fiscalCode)
    {
        return recipientsApi.ensureRecipientByExternalId(isPerson?RecipientTypeDto.PF:RecipientTypeDto.PG, fiscalCode);
    }

    /**
     * Salva per una certa delega, le info riguardanti il DELEGATO
     *
     * @param mandateId id della delega
     * @param name nome
     * @param surname cognome
     * @param businessName ragione sociale
     * @return void
     */
    public Mono<String> updateMandateById(String mandateId, String name, String surname, String businessName)
    {
        DenominationDtoDto addressdto = new DenominationDtoDto();
        addressdto.setDestName(name);
        addressdto.setDestSurname(surname);
        addressdto.setDestBusinessName(businessName);
        return mandatesApi.updateMandateById(mandateId, addressdto)
                .then(Mono.just("OK"));
    }

    /**
     * Elimina le informazioni per una certa delega
     *
     * @param mandateId id della delega
     * @return void
     */
    public Mono<Void> deleteMandateById(String mandateId)
    {
        return mandatesApi.deleteMandateById(mandateId);
    }

    /**
     * Ritorna le info sui DELEGATI in base agli id delle deleghe passati
     * @param mandateIds lista di id deleghe
     * @return lista userinfo deleghe
     */
    public Flux<MandateDtoDto> getMandatesByIds(List<String> mandateIds)
    {                
        return mandatesApi.getMandatesByIds(mandateIds);
    }

}
