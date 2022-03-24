package it.pagopa.pn.mandate.middleware.db;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import it.pagopa.pn.mandate.middleware.db.entities.MandateEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;

@Repository
public class MandateDao extends BaseDao {

    public static final String MANDATE_PREFIX = "MANDATE-";
    public static final String DELEGATOR_PREFIX = "USERDELEGATOR-";

    //private DynamoDbEnhancedAsyncClient dynamoDbEnhancedClient; 
    DynamoDbAsyncTable<MandateEntity> mandateTable;
    

    public MandateDao(DynamoDbEnhancedAsyncClient dynamoDbAsyncClient,
                              @Value("${aws.dynamodb.table}") String table) {
        //this.dynamoDbEnhancedClient = dynamoDbAsyncClient;
        this.mandateTable = dynamoDbAsyncClient.table(table, TableSchema.fromBean(MandateEntity.class));            
    }

    public Flux<MandateEntity> listMandatesByDelegate(String internaluserid) {
        QueryEnhancedRequest qeRequest = QueryEnhancedRequest
                .builder()
                .queryConditional(QueryConditional.sortBeginsWith(getKeyBuild(DELEGATOR_PREFIX + internaluserid, MANDATE_PREFIX)))
                .scanIndexForward(true)                
                .build();

        return Flux.from(mandateTable.query(qeRequest).items());
    }

    public Flux<MandateEntity> listMandatesByDelegator(String internaluserid) {
        QueryEnhancedRequest qeRequest = QueryEnhancedRequest
                .builder()
                .queryConditional(QueryConditional.sortBeginsWith(getKeyBuild(DELEGATOR_PREFIX + internaluserid, MANDATE_PREFIX)))
                .scanIndexForward(true)
                .build();

        return Flux.from(mandateTable.query(qeRequest).items());
    }
 

    public Mono<Object> updateMandate() {
        return Mono.empty();
    /*
    ScanRequest scanRequest = ScanRequest.builder()
            .tableName(customerTable)
            .build();

    return Mono.fromCompletionStage(dynamoDbAsyncClient.scan(scanRequest))
            .map(scanResponse -> scanResponse.items())
            .map(CustomerMapper::fromList)
            .flatMapMany(Flux::fromIterable);*/
}
/*
    public Mono<Mandate> createCustomer(Mandate customer) {

        customer.setPk(UUID.randomUUID().toString());

        PutItemRequest putItemRequest = PutItemRequest.builder()
                .tableName(customerTable)
                .item(CustomerMapper.toMap(customer))
                .build();

        return Mono.fromCompletionStage(dynamoDbAsyncClient.putItem(putItemRequest))
                .map(putItemResponse -> putItemResponse.attributes())
                .map(attributeValueMap -> customer);
    }

    public Mono<String> deleteCustomer(String customerId) {
        DeleteItemRequest deleteItemRequest = DeleteItemRequest.builder()
                .tableName(customerTable)
                .key(Map.of("customerId", AttributeValue.builder().s(customerId).build()))
                .build();

        return Mono.fromCompletionStage(dynamoDbAsyncClient.deleteItem(deleteItemRequest))
                .map(deleteItemResponse -> deleteItemResponse.attributes())
                .map(attributeValueMap -> customerId);
    }

    public Mono<Mandate> getCustomer(String customerId) {
        GetItemRequest getItemRequest = GetItemRequest.builder()
                .tableName(customerTable)
                .key(Map.of("customerId", AttributeValue.builder().s(customerId).build()))
                .build();

        return Mono.fromCompletionStage(dynamoDbAsyncClient.getItem(getItemRequest))
                .map(getItemResponse -> getItemResponse.item())
                .map(CustomerMapper::fromMap);
    }

    public Mono<String> updateCustomer(String customerId, Mandate customer) {

        customer.setId(customerId);
        PutItemRequest putItemRequest = PutItemRequest.builder()
                .tableName(customerTable)
                .item(CustomerMapper.toMap(customer))
                .build();

        return Mono.fromCompletionStage(dynamoDbAsyncClient.putItem(putItemRequest))
                .map(updateItemResponse -> customerId);
    }*/



}
