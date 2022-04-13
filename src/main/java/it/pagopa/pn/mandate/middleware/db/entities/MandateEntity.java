package it.pagopa.pn.mandate.middleware.db.entities;

import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

import java.util.Set;

import static it.pagopa.pn.mandate.middleware.db.BaseDao.GSI_INDEX_DELEGATE_STATE;

/**
 * Entity Delega
 */
@DynamoDbBean
@Data
@NoArgsConstructor
public class MandateEntity {

    public static final String MANDATE_PREFIX = "MANDATE#";

    public static final String COL_PK = "pk";
    public static final String COL_SK = "sk";
    public static final String COL_S_DELEGATE = "s_delegate";
    public static final String COL_I_STATE = "i_state";
    public static final String COL_B_DELEGATORISPERSON = "b_delegatorisperson";
    public static final String COL_B_DELEGATEISPERSON = "b_delegateisperson";
    public static final String COL_D_VALIDFROM = "d_validfrom";
    public static final String COL_D_VALIDTO = "d_validto";
    public static final String COL_S_MANDATEID = "s_mandateid";

    public MandateEntity(String delegator, String mandateid)
    {
        this.setDelegator(delegator);
        this.setMandateId(mandateid);
    }

    @DynamoDbAttribute(COL_S_MANDATEID)
    public String getMandateId(){
        return this.sk.replace(MANDATE_PREFIX, "");
    }
    public void setMandateId(String id){
        this.sk = MANDATE_PREFIX + id;
    }

    @Getter(onMethod=@__({@DynamoDbPartitionKey, @DynamoDbAttribute(COL_PK)})) private String delegator;
    @Getter(onMethod=@__({@DynamoDbSortKey, @DynamoDbAttribute(COL_SK)}))  private String sk;


    @Getter(onMethod=@__({@DynamoDbSecondaryPartitionKey(indexNames = { GSI_INDEX_DELEGATE_STATE}), @DynamoDbAttribute(COL_S_DELEGATE)}))  private String delegate;
    @Getter(onMethod=@__({@DynamoDbSecondarySortKey(indexNames = { GSI_INDEX_DELEGATE_STATE }), @DynamoDbAttribute(COL_I_STATE)}))  private int state;

    @Getter(onMethod=@__({@DynamoDbAttribute(COL_B_DELEGATORISPERSON)}))  private Boolean delegatorisperson;
    @Getter(onMethod=@__({@DynamoDbAttribute(COL_B_DELEGATEISPERSON)}))  private Boolean delegateisperson;

    @Getter(onMethod=@__({@DynamoDbAttribute(COL_D_VALIDFROM)}))  private String validfrom;
    @Getter(onMethod=@__({@DynamoDbAttribute(COL_D_VALIDTO)}))  private String validto;

    @Getter(onMethod=@__({@DynamoDbAttribute("t_created")}))  private String created;
    @Getter(onMethod=@__({@DynamoDbAttribute("t_accepted")}))  private String accepted;
    @Getter(onMethod=@__({@DynamoDbAttribute("t_rejected")}))  private String rejected;
    @Getter(onMethod=@__({@DynamoDbAttribute("t_revoked")}))  private String revoked;

    @Getter(onMethod=@__({@DynamoDbAttribute("s_validationcode")}))  private String validationcode;
    @Getter(onMethod=@__({@DynamoDbAttribute("a_visibilityids")}))  private Set<String> visibilityIds;


    // per lo storico e per la struttura dati di appoggio
    @Getter(onMethod=@__({@DynamoDbAttribute("i_ttl")}))  private Long ttl;
    
}
