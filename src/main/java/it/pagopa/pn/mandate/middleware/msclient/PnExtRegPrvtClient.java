package it.pagopa.pn.mandate.middleware.msclient;

import it.pagopa.pn.commons.log.PnLogger;
import it.pagopa.pn.commons.pnclients.CommonBaseClient;
import it.pagopa.pn.mandate.generated.openapi.msclient.extregselfcaregroups.v1.api.InternalOnlyApi;
import it.pagopa.pn.mandate.generated.openapi.msclient.extregselfcaregroups.v1.dto.PgGroupDto;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
@lombok.CustomLog
public class PnExtRegPrvtClient extends CommonBaseClient {

    private final InternalOnlyApi internalApi;

    public PnExtRegPrvtClient(InternalOnlyApi internalApi) {
        this.internalApi = internalApi;
    }


    public Flux<PgGroupDto> getGroups(String id) {
        log.logInvokingExternalService(PnLogger.EXTERNAL_SERVICES.PN_EXTERNAL_REGISTRIES, "Retrieving PG groups");
        return internalApi.getAllPgGroupsPrivate(id, null);
    }
}
