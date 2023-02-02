package it.pagopa.pn.mandate.middleware.queue.consumer;

import it.pagopa.pn.mandate.LocalStackTestConfig;
import it.pagopa.pn.mandate.exceptions.PnMandateNotFoundException;
import it.pagopa.pn.mandate.middleware.db.entities.MandateEntity;
import it.pagopa.pn.mandate.middleware.queue.consumer.event.PnMandateExpiredEvent;
import it.pagopa.pn.mandate.services.mandate.v1.MandateService;
import org.junit.jupiter.api.Assertions;
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
import java.util.function.Function;

@FunctionalSpringBootTest
@Import(LocalStackTestConfig.class)
class ExpiredMandatesHandlerTest {

    @Autowired
    private FunctionCatalog functionCatalog;
    @MockBean
    private MandateService mandateService;

//    @Test
//    void consumeMessageOK() {
//        Function<Flux<Message<PnMandateExpiredEvent.Payload>>, Mono<Void>> consumer = functionCatalog.lookup(Function.class, "pnMandateExpiredMandatesConsumer");
//        PnMandateExpiredEvent.Payload payload = PnMandateExpiredEvent.Payload.builder()
//                .mandateId("fb521b11-202d-452f-944e-88b1eb1c34bd")
//                .delegatorInternalUserid("PF-12345")
//                .build();
//        Message<PnMandateExpiredEvent.Payload> message = MessageBuilder.withPayload(payload).build();
//        Mockito.when(mandateService.expireMandate("fb521b11-202d-452f-944e-88b1eb1c34bd", "PF-12345"))
//                        .thenReturn(Mono.just(new MandateEntity()));
//
//        Mono<Void> result = consumer.apply(Flux.just(message));
//        StepVerifier.create(result)
//                        .expectComplete()
//                                .verify();
//        Mockito.verify(mandateService).expireMandate("fb521b11-202d-452f-944e-88b1eb1c34bd", "PF-12345");
//    }
//
//    @Test
//    void consumeMessageKO() {
//        Function<Flux<Message<PnMandateExpiredEvent.Payload>>, Mono<Void>> consumer = functionCatalog.lookup(Function.class, "pnMandateExpiredMandatesConsumer");
//        PnMandateExpiredEvent.Payload payload = PnMandateExpiredEvent.Payload.builder()
//                .mandateId("fb521b11-202d-452f-944e-88b1eb1c34bd")
//                .delegatorInternalUserid("PF-12345")
//                .build();
//        Message<PnMandateExpiredEvent.Payload> message = MessageBuilder.withPayload(payload).build();
//        Mockito.when(mandateService.expireMandate("fb521b11-202d-452f-944e-88b1eb1c34bd", "PF-12345"))
//                .thenReturn(Mono.error(new PnMandateNotFoundException()));
//
//        Mono<Void> result = consumer.apply(Flux.just(message));
//        StepVerifier.create(result)
//                .expectError(PnMandateNotFoundException.class)
//                .verify();
//
//        Mockito.verify(mandateService).expireMandate("fb521b11-202d-452f-944e-88b1eb1c34bd", "PF-12345");
//    }

    @Test
    void consumeMessageOK() {
        Consumer<Message<PnMandateExpiredEvent.Payload>> consumer = functionCatalog.lookup(Function.class, "pnMandateExpiredMandatesConsumer");
        PnMandateExpiredEvent.Payload payload = PnMandateExpiredEvent.Payload.builder()
                .mandateId("fb521b11-202d-452f-944e-88b1eb1c34bd")
                .delegatorInternalUserid("PF-12345")
                .build();
        Message<PnMandateExpiredEvent.Payload> message = MessageBuilder.withPayload(payload).build();
        Mockito.when(mandateService.expireMandate("fb521b11-202d-452f-944e-88b1eb1c34bd", "PF-12345"))
                .thenReturn(Mono.just(new MandateEntity()));

        consumer.accept(message);
        Mockito.verify(mandateService).expireMandate("fb521b11-202d-452f-944e-88b1eb1c34bd", "PF-12345");
    }

    @Test
    void consumeMessageKO() {
        Consumer<Message<PnMandateExpiredEvent.Payload>> consumer = functionCatalog.lookup(Function.class, "pnMandateExpiredMandatesConsumer");
        PnMandateExpiredEvent.Payload payload = PnMandateExpiredEvent.Payload.builder()
                .mandateId("fb521b11-202d-452f-944e-88b1eb1c34bd")
                .delegatorInternalUserid("PF-12345")
                .build();
        Message<PnMandateExpiredEvent.Payload> message = MessageBuilder.withPayload(payload).build();
        Mockito.when(mandateService.expireMandate("fb521b11-202d-452f-944e-88b1eb1c34bd", "PF-12345"))
                .thenReturn(Mono.error(new PnMandateNotFoundException()));

        Assertions.assertThrows(PnMandateNotFoundException.class, () ->  consumer.accept(message));
        Mockito.verify(mandateService).expireMandate("fb521b11-202d-452f-944e-88b1eb1c34bd", "PF-12345");
    }

}
