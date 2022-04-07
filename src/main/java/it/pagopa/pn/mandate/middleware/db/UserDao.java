package it.pagopa.pn.mandate.middleware.db;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import it.pagopa.pn.mandate.mapper.StatusEnumMapper;
import it.pagopa.pn.mandate.middleware.db.entities.MandateEntity;
import it.pagopa.pn.mandate.rest.mandate.v1.dto.MandateDto;
import it.pagopa.pn.mandate.utils.DateUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import it.pagopa.pn.mandate.middleware.db.entities.UserEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.*;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.Select;

@Repository
@Slf4j
public class UserDao extends BaseDao {

    DynamoDbAsyncTable<UserEntity> userTable;
    DynamoDbAsyncClient dynamoDbAsyncClient;
    String table ;

    public static final String DELEGATE_PREFIX = "USERDELEGATE-";
    public static final String TOTALS = "TOTALS";
   

    public UserDao(DynamoDbEnhancedAsyncClient dynamoDbEnhancedAsyncClient,
                   DynamoDbAsyncClient dynamoDbAsyncClient,
                              @Value("${aws.dynamodb.table}") String table) {
        this.userTable = dynamoDbEnhancedAsyncClient.table(table, TableSchema.fromBean(UserEntity.class));
        this.dynamoDbAsyncClient = dynamoDbAsyncClient;
        this.table = table;
    }

    public Mono<UserEntity> countMandates(String internaluserid) {
        // per ora l'unico stato supportato per il count è il pending, il filtro sullo stato viene quindi omesso volutamente.
        GetItemEnhancedRequest giRequest = GetItemEnhancedRequest.builder()
                .key(getKeyBuild(internaluserid, TOTALS))
                .build();
        
        CompletableFuture<UserEntity> user = userTable.getItem(giRequest)
                .whenComplete((cus, ex) -> {
                        
                    if (null == cus) {
                            throw new IllegalArgumentException("Invalid user");
                    }
                });
        return Mono.fromFuture(user);
    }


    public Mono<Object> updateUserPendingCount(String delegate_internaluserid){
        log.info("Updating user pending count uid:{}", delegate_internaluserid);
        // qui l'internaluserid è quello del DELEGATO, devo passare quindi per l'indice sul delegato,
        // e fare il count delle deleghe in pending.
        Map<String, AttributeValue> expressionValues = new HashMap<>();
        expressionValues.put(":delegate", AttributeValue.builder().s(delegate_internaluserid).build());
        expressionValues.put(":state", AttributeValue.builder().n(StatusEnumMapper.intValfromStatus(MandateDto.StatusEnum.PENDING) + "").build());  //anche se numero, va convertito in stringa, a dynamo piace così

        QueryRequest qeRequest = QueryRequest
                .builder()
                .select(Select.COUNT)
                .tableName(table)
                .indexName(GSI_INDEX_DELEGATE_STATE)
                .keyConditionExpression(MandateEntity.COL_S_DELEGATE + " = :delegate AND " + MandateEntity.COL_I_STATE + " = :state")
                .expressionAttributeValues(expressionValues)
                //.queryConditional(QueryConditional.keyEqualTo(getKeyBuild(delegator_internaluserid, StatusEnumMapper.intValfromStatus(MandateDto.StatusEnum.PENDING))))
                .build();

        return Mono.just(dynamoDbAsyncClient.query(qeRequest).thenApply(x -> {
            UserEntity user =new UserEntity();
            user.setPk(delegate_internaluserid);
            user.setSk(TOTALS);
            user.setPendingcount(x.count());

            PutItemEnhancedRequest<UserEntity> puReq = PutItemEnhancedRequest.builder(UserEntity.class)
                    .item(user)
                    .build();
            return userTable.putItem(puReq).thenApply(r -> {
                log.info("Updated user pending count uid:{} pendingcount:{}", delegate_internaluserid, x.count());
                return user;
            });
        }));

    }
}
