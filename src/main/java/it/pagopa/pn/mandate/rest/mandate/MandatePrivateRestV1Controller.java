package it.pagopa.pn.mandate.rest.mandate;

import it.pagopa.pn.mandate.rest.mandate.v1.api.MandatePrivateServiceApi;
import it.pagopa.pn.mandate.rest.mandate.v1.dto.*;
import it.pagopa.pn.mandate.services.mandate.v1.MandatePrivateService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@lombok.CustomLog
public class MandatePrivateRestV1Controller implements MandatePrivateServiceApi {

    MandatePrivateService mandateService;

    public MandatePrivateRestV1Controller(MandatePrivateService mandateService) {
        this.mandateService = mandateService;
    }

    @Override
    public Mono<ResponseEntity<Flux<InternalMandateDto>>> listMandatesByDelegate(String internaluserId,
                                                                                 CxTypeAuthFleet xPagopaPnCxType,
                                                                                 String mandateId,
                                                                                 List<String> cxGroups,
                                                                                 ServerWebExchange exchange) {
        log.logStartingProcess("listing mandates by delegate");
        return mandateService.listMandatesByDelegate(internaluserId, mandateId,xPagopaPnCxType, cxGroups)
                .collectList()
                .map(m -> ResponseEntity.status(HttpStatus.OK).body(Flux.fromIterable(m)))
                .doOnNext(m -> log.logEndingProcess("private listing mandates by delegate"));
    }

    @Override
    public Mono<ResponseEntity<Flux<InternalMandateDto>>> listMandatesByDelegator(String internaluserId,
                                                                                  CxTypeAuthFleet xPagopaPnCxType,
                                                                                  String mandateId,
                                                                                  List<String> cxGroups,
                                                                                  String cxRole,
                                                                                  DelegateType delegateType,
                                                                                  final ServerWebExchange exchange) {
        log.logStartingProcess("private listing mandates by delegator");
        return mandateService.listMandatesByDelegator(internaluserId, mandateId, xPagopaPnCxType, cxGroups, cxRole, delegateType)
                .collectList()
                .map(m -> ResponseEntity.status(HttpStatus.OK).body(Flux.fromIterable(m)))
                .doOnNext(m -> log.logEndingProcess("private listing mandates by delegator"));
    }

    @Override
    public Mono<ResponseEntity<Flux<InternalMandateDto>>> listMandatesByDelegators(DelegateType delegateType,
                                                                                   List<String> delegateGroups,
                                                                                   Flux<MandateByDelegatorRequestDto> mandateByDelegatorRequestDto,
                                                                                   final ServerWebExchange exchange) {
        log.logStartingProcess("private listing mandates by delegators");
        return mandateService.listMandatesByDelegators(delegateType, delegateGroups, mandateByDelegatorRequestDto)
                .collectList()
                .map(m -> ResponseEntity.status(HttpStatus.OK).body(Flux.fromIterable(m)))
                .doOnNext(m -> log.logEndingProcess("private listing mandates by delegators"));
    }
}
