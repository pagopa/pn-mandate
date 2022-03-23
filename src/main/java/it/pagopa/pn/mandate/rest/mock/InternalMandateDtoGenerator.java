package it.pagopa.pn.mandate.rest.mock;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.UUID;

import it.pagopa.pn.mandate.rest.mandate.v1.dto.InternalMandateDto;
import it.pagopa.pn.mandate.rest.mandate.v1.dto.MandateDto.StatusEnum;
import it.pagopa.pn.mandate.utils.DateUtils;

public class InternalMandateDtoGenerator {
    
    public static InternalMandateDto generate(StatusEnum state, boolean isdelegator){
        InternalMandateDto dto = new InternalMandateDto();
        dto.datefrom(DateUtils.formatDate(LocalDate.now().minusDays(120)));
        dto.dateto(DateUtils.formatDate(LocalDate.now().plusYears(1)));
        dto.delegator("internaluuid"+UUID.randomUUID().toString());
        dto.delegate("internaluuid2"+UUID.randomUUID().toString());        
        dto.setMandateId(UUID.randomUUID().toString());
        dto.setVisibilityIds(new ArrayList<>());
        
        return dto;
    }

}
