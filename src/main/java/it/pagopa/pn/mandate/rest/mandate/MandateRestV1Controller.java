package it.pagopa.pn.mandate.rest.mandate;

import it.pagopa.pn.commons.log.PnAuditLogBuilder;
import it.pagopa.pn.commons.log.PnAuditLogEvent;
import it.pagopa.pn.commons.log.PnAuditLogEventType;
import it.pagopa.pn.mandate.rest.mandate.v1.api.MandateServiceApi;
import it.pagopa.pn.mandate.rest.mandate.v1.dto.AcceptRequestDto;
import it.pagopa.pn.mandate.rest.mandate.v1.dto.CxTypeAuthFleet;
import it.pagopa.pn.mandate.rest.mandate.v1.dto.MandateCountsDto;
import it.pagopa.pn.mandate.rest.mandate.v1.dto.MandateDto;
import it.pagopa.pn.mandate.services.mandate.v1.MandateService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
public class MandateRestV1Controller  implements MandateServiceApi   {

    MandateService mandateService;

    public MandateRestV1Controller(MandateService mandateService) {
        this.mandateService = mandateService;
    }

    @Override
    public Mono<ResponseEntity<Void>> acceptMandate(String xPagopaPnCxId, CxTypeAuthFleet xPagopaPnCxType, String mandateId, Mono<AcceptRequestDto> acceptRequestDto,
                                                    ServerWebExchange exchange) {
        String logMessage = String.format("acceptMandate - xPagopaPnCxId=%s - consentType=%s", xPagopaPnCxId);
        PnAuditLogBuilder auditLogBuilder = new PnAuditLogBuilder();
        PnAuditLogEvent logEvent = auditLogBuilder
                .before(PnAuditLogEventType.AUD_DL_ACCEPT, logMessage)
                .cxId(xPagopaPnCxId)
                .build();
        return  mandateService.acceptMandate(mandateId, acceptRequestDto, xPagopaPnCxId)
                .onErrorResume(throwable -> {
                    logEvent.generateFailure(throwable.getMessage()).log();
                    return Mono.error(throwable);
                })
                .then(Mono.just(logEvent.generateSuccess().log()))
                .then(Mono.just(ResponseEntity.noContent().build()));
    }

    @Override
    public Mono<ResponseEntity<MandateCountsDto>> countMandatesByDelegate(String xPagopaPnCxId, CxTypeAuthFleet xPagopaPnCxType, String status, ServerWebExchange exchange) {
        return  mandateService.countMandatesByDelegate(status, xPagopaPnCxId)
            .map(m -> ResponseEntity.status(HttpStatus.OK).body(m));    
    }

    @Override
    public Mono<ResponseEntity<MandateDto>> createMandate(String xPagopaPnCxId, CxTypeAuthFleet xPagopaPnCxType, Mono<MandateDto> mandateDto, ServerWebExchange exchange) {
        PnAuditLogBuilder auditLogBuilder = new PnAuditLogBuilder();
        String logMessage = String.format("createMandate - xPagopaPnCxId=%s - consentType=%s", xPagopaPnCxId);
        PnAuditLogEvent logEvent = auditLogBuilder
                .before(PnAuditLogEventType.AUD_DL_CREATE, logMessage)
                .cxId(xPagopaPnCxId)
                .build();
        return  mandateService
            .createMandate(mandateDto, xPagopaPnCxId, (xPagopaPnCxType==null || xPagopaPnCxType.equals(CxTypeAuthFleet.PF)))
            .map(m ->  {
                logEvent.generateSuccess(logMessage).log();
                return ResponseEntity.status(HttpStatus.CREATED).body(m);
            });
         
    }

    @Override
    public Mono<ResponseEntity<Flux<MandateDto>>> listMandatesByDelegate1(String xPagopaPnCxId, CxTypeAuthFleet xPagopaPnCxType, String status, ServerWebExchange exchange) {

        return  mandateService.listMandatesByDelegate(status, xPagopaPnCxId)
            .collectList()     
            .map(m -> ResponseEntity.status(HttpStatus.OK).body(Flux.fromIterable(m)));     
    }

    @Override
    public Mono<ResponseEntity<Flux<MandateDto>>> listMandatesByDelegator1(String xPagopaPnCxId, CxTypeAuthFleet xPagopaPnCxType, ServerWebExchange exchange) {

        return  mandateService.listMandatesByDelegator(xPagopaPnCxId)
            .collectList()     
            .map(m -> ResponseEntity.status(HttpStatus.OK).body(Flux.fromIterable(m)));     
    }

    @Override
    public Mono<ResponseEntity<Void>> rejectMandate(String xPagopaPnCxId, CxTypeAuthFleet xPagopaPnCxType, String mandateId, ServerWebExchange exchange) {
        String logMessage = String.format("rejectMandate - xPagopaPnCxId=%s", xPagopaPnCxId);
        PnAuditLogBuilder auditLogBuilder = new PnAuditLogBuilder();
        PnAuditLogEvent logEvent = auditLogBuilder
                .before(PnAuditLogEventType.AUD_DL_REJECT, logMessage)
                .cxId(xPagopaPnCxId)
                .build();
        return  mandateService.rejectMandate(mandateId, xPagopaPnCxId)
                .onErrorResume(throwable -> {
                    logEvent.generateFailure(throwable.getMessage()).log();
                    return Mono.error(throwable);
                })
            .then(Mono.just(ResponseEntity.noContent().build()));
    }

    @Override
    public Mono<ResponseEntity<Void>> revokeMandate(String xPagopaPnCxId, CxTypeAuthFleet xPagopaPnCxType, String mandateId, ServerWebExchange exchange) {
        String logMessage = String.format("revokeMandate - xPagopaPnCxId=%s", xPagopaPnCxId);
        PnAuditLogBuilder auditLogBuilder = new PnAuditLogBuilder();
        PnAuditLogEvent logEvent = auditLogBuilder
                .before(PnAuditLogEventType.AUD_DL_REVOKE, logMessage)
                .build();
        return  mandateService.revokeMandate(mandateId, xPagopaPnCxId)
                .onErrorResume(throwable -> {
                    logEvent.generateFailure(throwable.getMessage()).log();
                    return Mono.error(throwable);
                })
        .map(m -> {
            logEvent.generateSuccess(logMessage).log();
            return ResponseEntity.noContent().build();
        });
    }
}
