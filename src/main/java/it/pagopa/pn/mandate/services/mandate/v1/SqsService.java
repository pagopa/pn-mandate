package it.pagopa.pn.mandate.services.mandate.v1;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.pagopa.pn.commons.exceptions.PnInternalException;
import it.pagopa.pn.mandate.model.mandate.SqsToDeliveryMessageDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

import static it.pagopa.pn.mandate.exceptions.PnMandateExceptionCodes.ERROR_CODE_JSON_PROCESSING_SQS_SERVICE;

@Slf4j
@Component
public class SqsService {

    private final SqsClient sqsClient;
    private final ObjectMapper mapper;
    private final String toDeliveryQueueName;

    public SqsService(
            @Value("${pn.mandate.sqs.to.pn.delivery.name}")String toDeliveryQueueName,
            SqsClient sqsClient,
            ObjectMapper mapper) {
        this.sqsClient = sqsClient;
        this.mapper = mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        this.toDeliveryQueueName = toDeliveryQueueName;
    }

    public Mono<SendMessageResponse> push(SqsToDeliveryMessageDto msg) {

        log.info("Creating message request to write on the {}.", toDeliveryQueueName);

        GetQueueUrlRequest getQueueRequest = GetQueueUrlRequest.builder()
                .queueName(toDeliveryQueueName)
                .build();
        String queueUrl = sqsClient.getQueueUrl(getQueueRequest).queueUrl();

        log.debug("Queue name: {}; Queue URL: {}.", toDeliveryQueueName, queueUrl);

        SendMessageRequest sendMsgRequest = SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(toJson(msg))
                .build();

        log.debug("SendMessageRequest: {}.", sendMsgRequest);

        return Mono.fromCallable(() -> sqsClient.sendMessage(sendMsgRequest));
    }

    private String toJson(SqsToDeliveryMessageDto sqsToDeliveryMessageDto) {
        try {
            return mapper.writeValueAsString(sqsToDeliveryMessageDto);
        } catch (JsonProcessingException e) {
            throw new PnInternalException("Error during json processing - SqsService.", ERROR_CODE_JSON_PROCESSING_SQS_SERVICE, e);
        }
    }
}
