package it.pagopa.pn.mandate.middleware.db;

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

    DynamoDbAsyncTable<UserEntity> userTable;
    public static final String DELEGATE_PREFIX = "USERDELEGATE-";
   

    public UserDao(DynamoDbEnhancedAsyncClient dynamoDbAsyncClient,
                              @Value("${aws.dynamodb.table}") String table) {
        this.userTable = dynamoDbAsyncClient.table(table, TableSchema.fromBean(UserEntity.class));        
    }

    public Mono<UserEntity> countMandates(String internaluserid) {
        // per ora l'unico stato supportato per il count è il pending, il filtro sullo stato viene quindi omesso volutamente.
        GetItemEnhancedRequest giRequest = GetItemEnhancedRequest.builder()
                .key(getKeyBuild(internaluserid, "TOTALS"))        
                .build();
        
        CompletableFuture<UserEntity> user = userTable.getItem(giRequest)
                .whenComplete((cus, ex) -> {
                        
                    if (null == cus) {
                            throw new IllegalArgumentException("Invalid customerId");                    
                    }
                });
        return Mono.fromFuture(user);
    }
}
