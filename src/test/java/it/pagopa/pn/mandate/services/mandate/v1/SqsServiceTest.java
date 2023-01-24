package it.pagopa.pn.mandate.services.mandate.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.pagopa.pn.api.dto.events.EventType;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlResponse;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SqsServiceTest {

    @Test
    void testPush() {
        SqsClient amazonSQS = mock(SqsClient.class);
        GetQueueUrlResponse getQueueUrlResponse = GetQueueUrlResponse.builder().queueUrl("queueUrl").build();

        when(amazonSQS.getQueueUrl((GetQueueUrlRequest) any())).thenReturn(getQueueUrlResponse);

        SqsService sqsService = new SqsService("queueNameTest", amazonSQS, new ObjectMapper());

        assertDoesNotThrow(() -> sqsService.sentToDelivery("mandateId", EventType.MANDATE_ACCEPTED));
    }

}

