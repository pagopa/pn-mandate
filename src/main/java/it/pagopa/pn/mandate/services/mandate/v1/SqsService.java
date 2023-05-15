package it.pagopa.pn.mandate.services.mandate.v1;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.pagopa.pn.api.dto.events.EventPublisher;
import it.pagopa.pn.api.dto.events.EventType;
import it.pagopa.pn.api.dto.events.GenericEventHeader;
import it.pagopa.pn.api.dto.events.PnMandateEvent;
import it.pagopa.pn.commons.exceptions.PnInternalException;
import it.pagopa.pn.mandate.middleware.db.entities.MandateEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static it.pagopa.pn.mandate.exceptions.PnMandateExceptionCodes.ERROR_CODE_JSON_PROCESSING_SQS_SERVICE;

@lombok.CustomLog
@Component
public class SqsService {

    private final SqsClient sqsClient;
    private final ObjectMapper mapper;
    private final String toDeliveryQueueName;

    private static final String STRING_TYPE = "String";

    public SqsService(@Value("${pn.mandate.sqs.to.pn.delivery.name}")String toDeliveryQueueName,
                      SqsClient sqsClient,
                      ObjectMapper mapper) {
        this.sqsClient = sqsClient;
        this.mapper = mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        this.toDeliveryQueueName = toDeliveryQueueName;
    }

    public Mono<SendMessageResponse> sendToDelivery(MandateEntity entity, EventType eventType) {
        log.debug("Inserting data {} in SQS {}", entity, toDeliveryQueueName);

        log.debug("get queue url request from queue name: {}", toDeliveryQueueName);
        GetQueueUrlRequest getQueueRequest = GetQueueUrlRequest.builder()
                .queueName(toDeliveryQueueName)
                .build();
        String queueUrl = sqsClient.getQueueUrl(getQueueRequest).queueUrl();
        log.debug("Queue name: {}; Queue URL: {}.", toDeliveryQueueName, queueUrl);

        String eventId = "mandate_"  + entity.getMandateId() + "_" + eventType;
        PnMandateEvent.Payload msg = PnMandateEvent.Payload.builder()
                .mandateId(entity.getMandateId())
                .delegateId(entity.getDelegate())
                .delegatorId(entity.getDelegator())
                .visibilityIds(entity.getVisibilityIds())
                .groups(entity.getGroups())
                .validFrom(entity.getValidfrom())
                .validTo(entity.getValidto())
                .build();

        SendMessageRequest sendMsgRequest = SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageAttributes(buildMessageAttributeMap(eventId, eventType))
                .messageBody(toJson(msg))
                .build();

        log.debug("SendMessageRequest: {}", sendMsgRequest);

        return Mono.fromCallable(() -> sqsClient.sendMessage(sendMsgRequest))
                .doOnNext(m -> log.info("Inserted data in SQS {}", toDeliveryQueueName));
    }

    private Map<String, MessageAttributeValue> buildMessageAttributeMap(String eventId, EventType eventType) {
        Map<String, MessageAttributeValue> attributes = new HashMap<>();
        Instant now = Instant.now();
        attributes.put(GenericEventHeader.PN_EVENT_HEADER_EVENT_ID, buildMessageAttributeValue(eventId));
        attributes.put(GenericEventHeader.PN_EVENT_HEADER_CREATED_AT, buildMessageAttributeValue(now.toString()));
        attributes.put(GenericEventHeader.PN_EVENT_HEADER_PUBLISHER, buildMessageAttributeValue(EventPublisher.MANDATE.name()));
        attributes.put(GenericEventHeader.PN_EVENT_HEADER_EVENT_TYPE, buildMessageAttributeValue(eventType.name()));
        return attributes;
    }

    private MessageAttributeValue buildMessageAttributeValue(String value) {
        return MessageAttributeValue.builder().stringValue(value).dataType(STRING_TYPE).build();
    }

    private String toJson(PnMandateEvent.Payload pnMandateEvent) {
        try {
            return mapper.writeValueAsString(pnMandateEvent);
        } catch (JsonProcessingException e) {
            throw new PnInternalException("can not send message to queue - error during json processing", ERROR_CODE_JSON_PROCESSING_SQS_SERVICE, e);
        }
    }
}
