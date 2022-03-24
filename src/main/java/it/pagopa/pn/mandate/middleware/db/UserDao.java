package it.pagopa.pn.mandate.middleware.db;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import it.pagopa.pn.mandate.middleware.db.entities.UserEntity;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.GetItemEnhancedRequest;

@Repository
public class UserDao extends BaseDao {

    //private DynamoDbEnhancedAsyncClient dynamoDbEnhancedClient; 
    DynamoDbAsyncTable<UserEntity> userTable;
   

    public UserDao(DynamoDbEnhancedAsyncClient dynamoDbAsyncClient,
                              @Value("${aws.dynamodb.table}") String table) {
        //this.dynamoDbEnhancedClient = dynamoDbAsyncClient;
        this.userTable = dynamoDbAsyncClient.table(table, TableSchema.fromBean(UserEntity.class));        
    }

    public Mono<UserEntity> countMandates(String internaluserid, Optional<String> status) {

        GetItemEnhancedRequest giRequest = GetItemEnhancedRequest.builder()
                .key(getKeyBuild(internaluserid, "TOTALS"))        
                .build();
        
        CompletableFuture<UserEntity> user = userTable.getItem(giRequest)
                .whenComplete((cus, ex) -> {
                        
                if (null == cus) {
                        throw new IllegalArgumentException("Invalid customerId");
                 //cus = new User();
                }
                });
        return Mono.fromFuture(user);
    }
}
