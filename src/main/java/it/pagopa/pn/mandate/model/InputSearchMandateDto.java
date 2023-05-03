package it.pagopa.pn.mandate.model;

import lombok.Builder;
import lombok.Data;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import java.util.List;

@Data
@Builder
public class InputSearchMandateDto {

    @NotEmpty
    private String delegateId;

    private List<Integer> statuses;

    private List<String> mandateIds;

    private List<String> groups;

    @NotNull
    @Positive
    private Integer size;

    private String nextPageKey;

    @NotNull
    @Positive
    private Integer maxPageNumber;
}
