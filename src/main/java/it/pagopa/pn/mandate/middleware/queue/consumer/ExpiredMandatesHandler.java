package it.pagopa.pn.mandate.middleware.queue.consumer;

import it.pagopa.pn.api.dto.events.PnMandateExpiredEvent;
import it.pagopa.pn.mandate.services.mandate.v1.MandateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import reactor.core.publisher.Mono;

import java.util.function.Consumer;

@Configuration
@Slf4j
@RequiredArgsConstructor
public class ExpiredMandatesHandler {
    private final MandateService mandateService;


    @Bean
    public Consumer<Mono<Message<PnMandateExpiredEvent.Payload>>> pnMandateExpiredMandatesConsumer() {
        return messageMono -> messageMono
                .doOnNext(message -> log.info("[enter] pnMandateExpiredMandatesConsumer, message {}", message))
                .map(Message::getPayload)
                .flatMap(payload -> mandateService.expireMandate(payload.getMandateId(), payload.getDelegatorInternalUserid()))
                .doOnSuccess(o -> log.info("[exit] pnMandateExpiredMandatesConsumer"))
                .onErrorResume(throwable -> HandleEventUtils.handleException(messageMono, throwable))
                .subscribe();
    }

}
