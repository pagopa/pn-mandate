package it.pagopa.pn.mandate.model.mandate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SqsToDeliveryMessageDto {

    public enum Action {
        ACCEPT,
        REVOKE,
        REJECT,
        EXPIRED
    }
    private String mandateId;
    private Action action;

}
