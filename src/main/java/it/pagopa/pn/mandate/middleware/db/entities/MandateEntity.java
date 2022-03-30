package it.pagopa.pn.mandate.middleware.db.entities;

import lombok.Data;
import lombok.Getter;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

import java.util.Set;

@DynamoDbBean
@Data
public class MandateEntity {
   
    @Getter(onMethod=@__({@DynamoDbPartitionKey, @DynamoDbAttribute("pk")})) private String delegator;
    @Getter(onMethod=@__({@DynamoDbSortKey, @DynamoDbAttribute("sk")}))  private String id;

    @Getter(onMethod=@__({@DynamoDbAttribute("s_delegate")}))  private String delegate;
    @Getter(onMethod=@__({@DynamoDbAttribute("i_state")}))  private int state;

    @Getter(onMethod=@__({@DynamoDbAttribute("d_validfrom")}))  private String validfrom;
    @Getter(onMethod=@__({@DynamoDbAttribute("d_validto")}))  private String validto;

    @Getter(onMethod=@__({@DynamoDbAttribute("t_created")}))  private String created;
    @Getter(onMethod=@__({@DynamoDbAttribute("t_accepted")}))  private String accepted;
    @Getter(onMethod=@__({@DynamoDbAttribute("t_rejected")}))  private String rejected;
    @Getter(onMethod=@__({@DynamoDbAttribute("t_revoked")}))  private String revoked;

    @Getter(onMethod=@__({@DynamoDbAttribute("s_validationcode")}))  private String validationcode;
    @Getter(onMethod=@__({@DynamoDbAttribute("a_visibilityids")}))  private Set<String> visibilityIds;


    // per lo storico e per la struttura dati di appoggio
    @Getter(onMethod=@__({@DynamoDbAttribute("i_ttl")}))  private Long ttl;
    
}
