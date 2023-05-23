package it.pagopa.pn.mandate.middleware.queue.consumer;

import it.pagopa.pn.commons.exceptions.PnInternalException;
import it.pagopa.pn.commons.log.MDCWebFilter;
import it.pagopa.pn.commons.utils.MDCUtils;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.cloud.function.context.MessageRoutingCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.util.StringUtils;

import java.util.Objects;
import java.util.UUID;

import static it.pagopa.pn.mandate.exceptions.PnMandateExceptionCodes.ERROR_CODE_MANDATE_HANDLER_NOT_PRESENT;
import static it.pagopa.pn.mandate.exceptions.PnMandateExceptionCodes.ERROR_CODE_MANDATE_INVALID_MESSAGE_HEADERS;

@Configuration
@lombok.CustomLog
@RequiredArgsConstructor
public class PnEventInboundService {

    private final EventHandler eventHandler;
    @Bean
    public MessageRoutingCallback customRouter() {
        return new MessageRoutingCallback() {
            @Override
            public FunctionRoutingResult routingResult(Message<?> message) {
                MessageHeaders messageHeaders = message.getHeaders();
                String traceId;
                String messageId = null;

                if (messageHeaders.containsKey("aws_messageId"))
                    messageId = messageHeaders.get("aws_messageId", String.class);

                traceId = Objects.requireNonNullElseGet(messageId, () -> "traceId:" + UUID.randomUUID());

                MDC.put(MDCUtils.MDC_TRACE_ID_KEY, traceId);
                MDC.put(MDCUtils.MDC_PN_CTX_MESSAGE_ID, messageId);
                return new FunctionRoutingResult(handleMessage(message));
            }
        };
    }

    private String handleMessage(Message<?> message) {
        log.debug("Message received from customRouter {}", message);
        String eventType = (String) message.getHeaders().get("eventType");
        log.info("Message received from customRouter with eventType = {}", eventType );
        if(eventType != null) {
            String handlerName = eventHandler.getHandler().get(eventType);
            if (!StringUtils.hasText(handlerName)) {
                log.error("Undefined handler for eventType={}", eventType);
                throw new PnInternalException(String.format("Undefined handler for eventType = %s", eventType), ERROR_CODE_MANDATE_HANDLER_NOT_PRESENT);
            }
            else {
                return handlerName;
            }
        }
        else {
            log.error("eventType not present, cannot start scheduled action headers={} payload={}", message.getHeaders(), message.getPayload());
            throw new PnInternalException("eventType not present, cannot start scheduled action", ERROR_CODE_MANDATE_INVALID_MESSAGE_HEADERS);
        }

    }

}
