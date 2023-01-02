package it.pagopa.pn.mandate.utils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class DynamoDbAsyncClientDecoratorTest {

    private DynamoDbAsyncClientDecorator dynamoDbAsyncClientDecorator;

    private DynamoDbAsyncClient delegate;

    @BeforeEach
    public void init() {
        delegate = Mockito.mock(DynamoDbAsyncClient.class);
        dynamoDbAsyncClientDecorator = new DynamoDbAsyncClientDecorator(delegate);
    }

    @Test
    void serviceNameTest() {
        Mockito.when(delegate.serviceName()).thenReturn("SERVICE");
        assertThat(dynamoDbAsyncClientDecorator.serviceName()).isEqualTo("SERVICE");
    }

    @Test
    void closeTest() {
        Assertions.assertDoesNotThrow(() -> dynamoDbAsyncClientDecorator.close());
    }


    @Test
    void queryTest() {
        QueryRequest mock = QueryRequest.builder().build();
        Mockito.when(delegate.query(mock)).thenReturn(CompletableFuture.completedFuture(QueryResponse.builder().build()));
        Assertions.assertDoesNotThrow(() -> dynamoDbAsyncClientDecorator.query(mock));
    }

}
