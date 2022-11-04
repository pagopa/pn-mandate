package it.pagopa.pn.mandate.middleware.db;

import it.pagopa.pn.mandate.config.PnMandateConfig;
import it.pagopa.pn.mandate.mapper.StatusEnumMapper;
import it.pagopa.pn.mandate.middleware.db.entities.DelegateEntity;
import it.pagopa.pn.mandate.middleware.db.entities.MandateEntity;
import it.pagopa.pn.mandate.rest.mandate.v1.dto.MandateDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.Select;

import java.util.HashMap;
import java.util.Map;

@Repository
@Slf4j
public class DelegateDao extends BaseDao {

    DynamoDbAsyncTable<DelegateEntity> userTable;
    DynamoDbAsyncClient dynamoDbAsyncClient;
    String table ;


    public DelegateDao(DynamoDbEnhancedAsyncClient dynamoDbEnhancedAsyncClient,
                       DynamoDbAsyncClient dynamoDbAsyncClient,
                       PnMandateConfig awsConfigs) {
        this.userTable = dynamoDbEnhancedAsyncClient.table(awsConfigs.getDynamodbTable(), TableSchema.fromBean(DelegateEntity.class));
        this.dynamoDbAsyncClient = dynamoDbAsyncClient;
        this.table = awsConfigs.getDynamodbTable();
    }

    public Mono<DelegateEntity> countMandates(String delegateInternaluserid) {
        if (log.isInfoEnabled())
            log.info("Get user pending count uid:{}", delegateInternaluserid);

        // qui l'internaluserid è quello del DELEGATO, devo passare quindi per l'indice sul delegato,
        // e fare il count delle deleghe in pending.
        Map<String, AttributeValue> expressionValues = new HashMap<>();
        expressionValues.put(":delegate", AttributeValue.builder().s(delegateInternaluserid).build());
        expressionValues.put(":state", AttributeValue.builder().n(StatusEnumMapper.intValfromStatus(MandateDto.StatusEnum.PENDING) + "").build());  //anche se numero, va convertito in stringa, a dynamo piace così

        QueryRequest qeRequest = QueryRequest
                .builder()
                .select(Select.COUNT)
                .tableName(table)
                .indexName(GSI_INDEX_DELEGATE_STATE)
                .keyConditionExpression(MandateEntity.COL_S_DELEGATE + " = :delegate AND " + MandateEntity.COL_I_STATE + " = :state")
                .expressionAttributeValues(expressionValues)
                .build();

        return Mono.fromFuture(dynamoDbAsyncClient.query(qeRequest).thenApply(x -> {
                DelegateEntity user =new DelegateEntity();
                user.setPendingcount(x.count());
                return user;
        }));
    }
}
