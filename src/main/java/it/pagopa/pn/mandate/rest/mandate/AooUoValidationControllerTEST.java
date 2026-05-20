package it.pagopa.pn.mandate.rest.mandate;

import it.pagopa.pn.mandate.generated.openapi.msclient.extregselfcare.v1.dto.FilteredPaIdsResponseDto;
import it.pagopa.pn.mandate.middleware.msclient.PnExtRegPrvtClient;
import lombok.CustomLog;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/mandate/api/v2/aoo-uo")
@CustomLog
public class AooUoValidationControllerTEST {

    private final PnExtRegPrvtClient pnExtRegPrvtClient;

    public AooUoValidationControllerTEST(PnExtRegPrvtClient pnExtRegPrvtClient) {
        this.pnExtRegPrvtClient = pnExtRegPrvtClient;
    }

    @PostMapping("/check")
    public Mono<FilteredPaIdsResponseDto> checkAooUoIds(@RequestBody List<String> senderIdList) {
        log.info("Checking AooUo IDs - size: {}", senderIdList.size());

        return pnExtRegPrvtClient.checkAooUoV2Ids(senderIdList).doOnNext(response -> log.info("Received response with {} IDs", response.getIds()));
    }
}