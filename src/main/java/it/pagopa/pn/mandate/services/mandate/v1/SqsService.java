package it.pagopa.pn.mandate.services.mandate.v1;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.pagopa.pn.api.dto.events.*;
import it.pagopa.pn.commons.exceptions.PnInternalException;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@Component
public class SqsService {

    private final SqsClient sqsClient;
    private final ObjectMapper mapper;
    private final String toDeliveryQueueName;

    public SqsService(@Value("${pn.mandate.sqs.to.pn.delivery.name}")String toDeliveryQueueName,
                      SqsClient sqsClient,
                      ObjectMapper mapper) {
        this.sqsClient = sqsClient;
        this.mapper = mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        this.toDeliveryQueueName = toDeliveryQueueName;
    }

    public Mono<SendMessageResponse> sentToDelivery(String mandateId, EventType eventType) {
        log.debug("get queue url request from queue name: {}", toDeliveryQueueName);
        GetQueueUrlRequest getQueueRequest = GetQueueUrlRequest.builder()
                .queueName(toDeliveryQueueName)
                .build();
        String queueUrl = sqsClient.getQueueUrl(getQueueRequest).queueUrl();
        log.debug("Queue name: {}; Queue URL: {}.", toDeliveryQueueName, queueUrl);

        String eventId = mandateId + "_mandate_" + eventType;
        PnMandateEvent.Payload msg = PnMandateEvent.Payload.builder()
                .mandateId(mandateId)
                .build();

        SendMessageRequest sendMsgRequest = SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageAttributes(buildMessageAttributeMap(eventId, eventType))
                .messageBody(toJson(msg))
                .build();

        log.debug("SendMessageRequest: {}", sendMsgRequest);

        return Mono.fromCallable(() -> sqsClient.sendMessage(sendMsgRequest));
    }

    private Map<String, MessageAttributeValue> buildMessageAttributeMap(String eventId, EventType eventType) {
        Map<String, MessageAttributeValue> attributes = new HashMap<>();
        Instant now = Instant.now();
        attributes.put(GenericEventHeader.PN_EVENT_HEADER_EVENT_ID, MessageAttributeValue.builder().stringValue(eventId).dataType("String").build());
        attributes.put(GenericEventHeader.PN_EVENT_HEADER_CREATED_AT, MessageAttributeValue.builder().stringValue(now.toString()).dataType("String").build());
        attributes.put(GenericEventHeader.PN_EVENT_HEADER_PUBLISHER, MessageAttributeValue.builder().stringValue(EventPublisher.MANDATE.name()).dataType("String").build());
        attributes.put(GenericEventHeader.PN_EVENT_HEADER_EVENT_TYPE, MessageAttributeValue.builder().stringValue(eventType.name()).dataType("String").build());
        return attributes;
    }

    private String toJson(PnMandateEvent.Payload pnMandateEvent) {
        try {
            return mapper.writeValueAsString(pnMandateEvent);
        } catch (JsonProcessingException e) {
            throw new PnInternalException("can not send message to queue - error during json processing", ERROR_CODE_JSON_PROCESSING_SQS_SERVICE, e);
        }
    }
}
