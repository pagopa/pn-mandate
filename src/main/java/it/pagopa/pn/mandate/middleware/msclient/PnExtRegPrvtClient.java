package it.pagopa.pn.mandate.middleware.msclient;

import it.pagopa.pn.commons.log.PnLogger;
import it.pagopa.pn.commons.pnclients.CommonBaseClient;
import it.pagopa.pn.mandate.config.PnMandateConfig;
import it.pagopa.pn.mandate.microservice.msclient.generated.extreg.prvt.v1.ApiClient;
import it.pagopa.pn.mandate.microservice.msclient.generated.extreg.prvt.v1.api.InternalOnlyApi;
import it.pagopa.pn.mandate.microservice.msclient.generated.extreg.prvt.v1.dto.PgGroupDto;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import javax.annotation.PostConstruct;

@Component
@lombok.CustomLog
public class PnExtRegPrvtClient extends CommonBaseClient {

    private InternalOnlyApi internalApi;
    private final PnMandateConfig pnMandateConfig;

    public PnExtRegPrvtClient(PnMandateConfig pnMandateConfig) {
        this.pnMandateConfig = pnMandateConfig;
    }

    @PostConstruct
    public void init() {
        ApiClient apiClient = new ApiClient(super.initWebClient(ApiClient.buildWebClientBuilder()));
        apiClient.setBasePath(pnMandateConfig.getClientExtregBasepath());
        internalApi = new InternalOnlyApi(apiClient);
    }

    public Flux<PgGroupDto> getGroups(String id) {
        log.logInvokingExternalService(PnLogger.EXTERNAL_SERVICES.PN_EXTERNAL_REGISTRIES, "Retrieving PG groups");
        return internalApi.getAllPgGroupsPrivate(id, null);
    }
}
