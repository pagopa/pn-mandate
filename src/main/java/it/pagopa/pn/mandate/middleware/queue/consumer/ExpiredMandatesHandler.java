package it.pagopa.pn.mandate.middleware.queue.consumer;

import it.pagopa.pn.commons.utils.MDCUtils;
import it.pagopa.pn.mandate.middleware.queue.consumer.event.PnMandateExpiredEvent;
import it.pagopa.pn.mandate.services.mandate.v1.MandateService;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

import java.util.function.Consumer;

@Configuration
@lombok.CustomLog
@RequiredArgsConstructor
public class ExpiredMandatesHandler {
    private final MandateService mandateService;

    //versione asincrona, bisogna aggiornare la versione di spring cloud stream per il supporto
//    @Bean
//    public Function<Flux<Message<PnMandateExpiredEvent.Payload>>, Mono<Void>> pnMandateExpiredMandatesConsumer() {
//        return messageFlux -> messageFlux
//                .doOnNext(message -> log.info("[enter] pnMandateExpiredMandatesConsumer, message {}", message))
//                .map(Message::getPayload)
//                .flatMap(payload -> mandateService.expireMandate(payload.getMandateId(), payload.getDelegatorInternalUserid()))
//                .doOnNext(o -> log.info("[exit] pnMandateExpiredMandatesConsumer"))
//                .onErrorResume(throwable -> HandleEventUtils.handleException(messageFlux, throwable))
//                .then();
//    }

    @Bean
    public Consumer<Message<PnMandateExpiredEvent.Payload>> pnMandateExpiredMandatesConsumer() {
        return message -> {
            String process = "expired mandate cleanup";
            try {
                log.logStartingProcess(process);
                log.debug("pnMandateExpiredMandatesConsumer, message {}", message);
                PnMandateExpiredEvent.Payload payload = message.getPayload();

                MDC.put(MDCUtils.MDC_PN_CTX_MANDATEID, payload.getMandateId());

                MDCUtils.addMDCToContextAndExecute(mandateService.expireMandate(payload.getMandateId(),
                        payload.getDelegatorInternalUserid(), payload.getDelegatorUserid(), payload.getDelegatorCxType())).block();
                log.logEndingProcess(process);
            }
            catch (Exception ex) {
                HandleEventUtils.handleException(message.getHeaders(), ex);
                throw ex;
            }


        };
    }

}
