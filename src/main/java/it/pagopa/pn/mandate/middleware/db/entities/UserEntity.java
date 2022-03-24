package it.pagopa.pn.mandate.middleware.db.entities;

import lombok.Data;
import lombok.Getter;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@DynamoDbBean
@Data
public class UserEntity {
   
    @Getter(onMethod=@__({@DynamoDbPartitionKey, @DynamoDbAttribute("pk")})) private String pk;
    @Getter(onMethod=@__({@DynamoDbSortKey, @DynamoDbAttribute("sk")})) private String sk;
    @Getter(onMethod=@__({@DynamoDbAttribute("i_pendingcount")})) private int pendingcount;
}
