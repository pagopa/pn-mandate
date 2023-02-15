package it.pagopa.pn.mandate.middleware.queue.consumer;

import it.pagopa.pn.mandate.middleware.queue.consumer.event.PnMandateExpiredEvent;
import it.pagopa.pn.mandate.services.mandate.v1.MandateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

import java.util.function.Consumer;

@Configuration
@Slf4j
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
            try {
                log.info("[enter] pnMandateExpiredMandatesConsumer, message {}", message);
                PnMandateExpiredEvent.Payload payload = message.getPayload();
                mandateService.expireMandate(payload.getMandateId(), payload.getDelegatorInternalUserid(), payload.getDelegatorUserid(), payload.getDelegatorCxType()).block();
                log.info("[exit] pnMandateExpiredMandatesConsumer");
            }
            catch (Exception ex) {
                HandleEventUtils.handleException(message.getHeaders(), ex);
                throw ex;
            }


        };
    }

}
