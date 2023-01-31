package it.pagopa.pn.mandate.middleware.queue.consumer;

import it.pagopa.pn.api.dto.events.PnMandateExpiredEvent;
import it.pagopa.pn.mandate.LocalStackTestConfig;
import it.pagopa.pn.mandate.middleware.db.entities.MandateEntity;
import it.pagopa.pn.mandate.services.mandate.v1.MandateService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.test.FunctionalSpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import reactor.core.publisher.Mono;

import java.util.function.Consumer;

@FunctionalSpringBootTest
@Import(LocalStackTestConfig.class)
class ExpiredMandatesHandlerTest {

    @Autowired
    private FunctionCatalog functionCatalog;
    @MockBean
    private MandateService mandateService;

    @Test
    void consumeMessageOK() {
        Consumer<Mono<Message<PnMandateExpiredEvent.Payload>>> consumer = functionCatalog.lookup(Consumer.class, "pnMandateExpiredMandatesConsumer");
        PnMandateExpiredEvent.Payload payload = PnMandateExpiredEvent.Payload.builder()
                .mandateId("mandateId")
                .delegatorInternalUserid("internalUserId")
                .build();
        Message<PnMandateExpiredEvent.Payload> message = MessageBuilder.withPayload(payload).build();
        Mockito.when(mandateService.expireMandate("mandateId", "internalUserId"))
                        .thenReturn(Mono.just(new MandateEntity()));
        consumer.accept(Mono.just(message));
        Mockito.verify(mandateService).expireMandate("mandateId", "internalUserId");
    }

    @Test
    void consumeMessageKO() {
        Consumer<Mono<Message<PnMandateExpiredEvent.Payload>>> consumer = functionCatalog.lookup(Consumer.class, "pnMandateExpiredMandatesConsumer");
        PnMandateExpiredEvent.Payload payload = PnMandateExpiredEvent.Payload.builder()
                .mandateId("mandateId")
                .delegatorInternalUserid("internalUserId")
                .build();
        Message<PnMandateExpiredEvent.Payload> message = MessageBuilder.withPayload(payload).build();
        Mockito.when(mandateService.expireMandate("mandateId", "internalUserId"))
                .thenReturn(Mono.error(new RuntimeException()));
        consumer.accept(Mono.just(message));
        Mockito.verify(mandateService).expireMandate("mandateId", "internalUserId");

    }

}
