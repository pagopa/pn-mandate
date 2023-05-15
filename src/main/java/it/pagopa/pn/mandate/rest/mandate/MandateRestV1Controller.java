package it.pagopa.pn.mandate.rest.mandate;

import it.pagopa.pn.mandate.rest.mandate.v1.api.MandateServiceApi;
import it.pagopa.pn.mandate.rest.mandate.v1.dto.*;
import it.pagopa.pn.mandate.services.mandate.v1.MandateService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@lombok.CustomLog
public class MandateRestV1Controller  implements MandateServiceApi {

    private final MandateService mandateService;

    public MandateRestV1Controller(MandateService mandateService) {
        this.mandateService = mandateService;
    }

    @Override
    public Mono<ResponseEntity<Void>> acceptMandate(String xPagopaPnCxId,
                                                    CxTypeAuthFleet xPagopaPnCxType,
                                                    String mandateId,
                                                    List<String> cxGroups,
                                                    String cxRole,
                                                    Mono<AcceptRequestDto> acceptRequestDto,
                                                    ServerWebExchange exchange) {

        log.logStartingProcess("accepting mandate");
        return  mandateService.acceptMandate(mandateId, acceptRequestDto, xPagopaPnCxId, xPagopaPnCxType, cxGroups, cxRole)
                .doOnNext(m -> log.logEndingProcess("accepting mandate"))
                .then(Mono.just(ResponseEntity.noContent().build()));

    }

    @Override
    public Mono<ResponseEntity<MandateCountsDto>> countMandatesByDelegate(String xPagopaPnCxId,
                                                                          CxTypeAuthFleet xPagopaPnCxType,
                                                                          List<String> cxGroups,
                                                                          String cxRole,
                                                                          String status,
                                                                          ServerWebExchange exchange) {

        log.logStartingProcess("counting mandates by delegate");
        return  mandateService.countMandatesByDelegate(status, xPagopaPnCxId, xPagopaPnCxType, cxGroups, cxRole)
            .map(m -> ResponseEntity.status(HttpStatus.OK).body(m))
            .doOnNext(m -> log.logEndingProcess("counting mandates by delegate"));
    }

    @Override
    public Mono<ResponseEntity<MandateDto>> createMandate(String xPagopaPnUid,
                                                          String xPagopaPnCxId,
                                                          CxTypeAuthFleet xPagopaPnCxType,
                                                          List<String> groups,
                                                          String role,
                                                          Mono<MandateDto> mandateDto,
                                                          final ServerWebExchange exchange) {

        log.logStartingProcess("creating new mandate");
        return  mandateService
                .createMandate(mandateDto, xPagopaPnUid, xPagopaPnCxId, xPagopaPnCxType, groups, role)
                .map(m ->  ResponseEntity.status(HttpStatus.CREATED).body(m))
                .doOnNext(m -> log.logEndingProcess("creating new mandate"));
    }

    @Override
    public Mono<ResponseEntity<Flux<MandateDto>>> listMandatesByDelegate1(String xPagopaPnCxId,
                                                                          CxTypeAuthFleet xPagopaPnCxType,
                                                                          List<String> xPagopaPnCxGroups,
                                                                          String xPagopaPnCxRole,
                                                                          String status,
                                                                          ServerWebExchange exchange) {
        log.logStartingProcess("listing mandates by delegate");
        return mandateService.listMandatesByDelegate(status, xPagopaPnCxId, xPagopaPnCxType, xPagopaPnCxGroups, xPagopaPnCxRole)
                .collectList()
                .map(m -> ResponseEntity.status(HttpStatus.OK).body(Flux.fromIterable(m)))
                .doOnNext(m -> log.logEndingProcess("listing mandates by delegate"));
    }

    @Override
    public Mono<ResponseEntity<SearchMandateResponseDto>> searchMandatesByDelegate(String xPagopaPnCxId,
                                                                                   CxTypeAuthFleet xPagopaPnCxType,
                                                                                   Integer size,
                                                                                   List<String> xPagopaPnCxGroups,
                                                                                   String xPagopaPnCxRole,
                                                                                   String nextPageKey,
                                                                                   Mono<SearchMandateRequestDto> searchMandateRequestDto,
                                                                                   final ServerWebExchange exchange) {
        log.logStartingProcess("search mandates by delegate");
        return mandateService.searchByDelegate(searchMandateRequestDto, size, nextPageKey, xPagopaPnCxId, xPagopaPnCxType, xPagopaPnCxGroups, xPagopaPnCxRole)
                .map(m -> ResponseEntity.status(HttpStatus.OK).body(m))
                .doOnNext(m -> log.logEndingProcess("search mandates by delegate"));
    }

    @Override
    public Mono<ResponseEntity<Flux<MandateDto>>> listMandatesByDelegator1(String xPagopaPnCxId,
                                                                           CxTypeAuthFleet xPagopaPnCxType,
                                                                           List<String> xPagopaPnCxGroups,
                                                                           String xPagopaPnCxRole,
                                                                           ServerWebExchange exchange) {
        log.logStartingProcess("listing mandates by delegator");
        return mandateService.listMandatesByDelegator(xPagopaPnCxId, xPagopaPnCxType, xPagopaPnCxGroups, xPagopaPnCxRole)
                .collectList()
                .map(m -> ResponseEntity.status(HttpStatus.OK).body(Flux.fromIterable(m)))
                .doOnNext(m -> log.logEndingProcess("listing mandates by delegator"));
    }

    @Override
    public Mono<ResponseEntity<Void>> rejectMandate(String xPagopaPnCxId,
                                                    CxTypeAuthFleet xPagopaPnCxType,
                                                    String mandateId,
                                                    List<String> xPagopaPnCxGroups,
                                                    String xPagopaPnCxRole,
                                                    ServerWebExchange exchange) {
        log.logStartingProcess("rejecting mandate");
        return mandateService.rejectMandate(mandateId, xPagopaPnCxId, xPagopaPnCxType, xPagopaPnCxRole, xPagopaPnCxGroups)
                .doOnNext(m -> log.logEndingProcess("rejecting mandate"))
                .then(Mono.just(ResponseEntity.noContent().build()));
    }

    @Override
    public Mono<ResponseEntity<Void>> revokeMandate(String xPagopaPnCxId,
                                                    CxTypeAuthFleet xPagopaPnCxType,
                                                    String mandateId,
                                                    List<String> xPagopaPnCxGroups,
                                                    String xPagopaPnCxRole,
                                                    ServerWebExchange exchange) {
        log.logStartingProcess("revoking mandate");
        return mandateService.revokeMandate(mandateId, xPagopaPnCxId, xPagopaPnCxType, xPagopaPnCxRole, xPagopaPnCxGroups)
                .doOnNext(m -> log.logEndingProcess("revoking mandate"))
                .map(m -> ResponseEntity.noContent().build());
    }
}
