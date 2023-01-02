package it.pagopa.pn.mandate.utils;

import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import software.amazon.awssdk.services.dynamodb.paginators.QueryPublisher;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class DynamoDbAsyncClientDecorator implements DynamoDbAsyncClient {

    private final DynamoDbAsyncClient dynamoDbAsyncClient;

    public DynamoDbAsyncClientDecorator(DynamoDbAsyncClient dynamoDbAsyncClient) {
        this.dynamoDbAsyncClient = dynamoDbAsyncClient;
    }

    @Override
    public String serviceName() {
        return this.dynamoDbAsyncClient.serviceName();
    }

    @Override
    public void close() {
        this.dynamoDbAsyncClient.close();
    }

    @Override
    public CompletableFuture<QueryResponse> query(Consumer<QueryRequest.Builder> queryRequest) {
        Map<String, String> copyOfContextMap = MDCUtils.retrieveMDCContextMap();
        return this.dynamoDbAsyncClient.query(queryRequest)
                .thenApply(queryResponse -> MDCUtils.enrichWithMDC(queryResponse, copyOfContextMap));
    }

    @Override
    public CompletableFuture<QueryResponse> query(QueryRequest queryRequest) {
        Map<String, String> copyOfContextMap = MDCUtils.retrieveMDCContextMap();
        return this.dynamoDbAsyncClient.query(queryRequest)
                .thenApply(queryResponse -> MDCUtils.enrichWithMDC(queryResponse, copyOfContextMap));
    }

    @Override
    public QueryPublisher queryPaginator(Consumer<QueryRequest.Builder> queryRequest) {
        return this.dynamoDbAsyncClient.queryPaginator(queryRequest);
    }

    @Override
    public QueryPublisher queryPaginator(QueryRequest queryRequest) {
        return this.dynamoDbAsyncClient.queryPaginator(queryRequest);
    }

    @Override
    public CompletableFuture<PutItemResponse> putItem(Consumer<PutItemRequest.Builder> putItemRequest) {
        Map<String, String> copyOfContextMap = MDCUtils.retrieveMDCContextMap();
        return this.dynamoDbAsyncClient.putItem(putItemRequest)
                .thenApply(putItemResponse -> MDCUtils.enrichWithMDC(putItemResponse, copyOfContextMap));
    }

    @Override
    public CompletableFuture<PutItemResponse> putItem(PutItemRequest putItemRequest) {
        Map<String, String> copyOfContextMap = MDCUtils.retrieveMDCContextMap();
        return this.dynamoDbAsyncClient.putItem(putItemRequest)
                .thenApply(putItemResponse -> MDCUtils.enrichWithMDC(putItemResponse, copyOfContextMap));
    }

    @Override
    public CompletableFuture<TransactWriteItemsResponse> transactWriteItems(Consumer<TransactWriteItemsRequest.Builder> transactWriteItemsRequest) {
        Map<String, String> copyOfContextMap = MDCUtils.retrieveMDCContextMap();
        return this.dynamoDbAsyncClient.transactWriteItems(transactWriteItemsRequest)
                .thenApply(transactWriteItemsResponse -> MDCUtils.enrichWithMDC(transactWriteItemsResponse, copyOfContextMap));
    }

    @Override
    public CompletableFuture<TransactWriteItemsResponse> transactWriteItems(TransactWriteItemsRequest transactWriteItemsRequest) {
        Map<String, String> copyOfContextMap = MDCUtils.retrieveMDCContextMap();
        return this.dynamoDbAsyncClient.transactWriteItems(transactWriteItemsRequest)
                .thenApply(transactWriteItemsResponse -> MDCUtils.enrichWithMDC(transactWriteItemsResponse, copyOfContextMap));
    }

    @Override
    public CompletableFuture<GetItemResponse> getItem(Consumer<GetItemRequest.Builder> getItemRequest) {
        Map<String, String> copyOfContextMap = MDCUtils.retrieveMDCContextMap();
        return this.dynamoDbAsyncClient.getItem(getItemRequest)
                .thenApply(getItemResponse -> MDCUtils.enrichWithMDC(getItemResponse, copyOfContextMap));
    }

    @Override
    public CompletableFuture<GetItemResponse> getItem(GetItemRequest getItemRequest) {
        Map<String, String> copyOfContextMap = MDCUtils.retrieveMDCContextMap();
        return this.dynamoDbAsyncClient.getItem(getItemRequest)
                .thenApply(getItemResponse -> MDCUtils.enrichWithMDC(getItemResponse, copyOfContextMap));
    }

    @Override
    public CompletableFuture<DeleteItemResponse> deleteItem(Consumer<DeleteItemRequest.Builder> deleteItemRequest) {
        Map<String, String> copyOfContextMap = MDCUtils.retrieveMDCContextMap();
        return this.dynamoDbAsyncClient.deleteItem(deleteItemRequest)
                .thenApply(deleteItemResponse -> MDCUtils.enrichWithMDC(deleteItemResponse, copyOfContextMap));
    }

    @Override
    public CompletableFuture<DeleteItemResponse> deleteItem(DeleteItemRequest deleteItemRequest) {
        Map<String, String> copyOfContextMap = MDCUtils.retrieveMDCContextMap();
        return this.dynamoDbAsyncClient.deleteItem(deleteItemRequest)
                .thenApply(deleteItemResponse -> MDCUtils.enrichWithMDC(deleteItemResponse, copyOfContextMap));
    }
}
