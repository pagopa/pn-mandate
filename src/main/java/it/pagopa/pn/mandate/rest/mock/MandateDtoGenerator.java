package it.pagopa.pn.mandate.rest.mock;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.UUID;

import it.pagopa.pn.mandate.rest.mandate.v1.dto.MandateDto;
import it.pagopa.pn.mandate.rest.mandate.v1.dto.UserDto;
import it.pagopa.pn.mandate.rest.mandate.v1.dto.MandateDto.StatusEnum;
import it.pagopa.pn.mandate.utils.DateUtils;

public class MandateDtoGenerator {
    
    public static MandateDto generate(StatusEnum state, boolean isdelegator){
        MandateDto dto = new MandateDto();
        dto.status(state);
        dto.datefrom(DateUtils.formatDate(LocalDate.now().minusDays(120)));
        dto.dateto(DateUtils.formatDate(LocalDate.now().plusYears(1)));
        if (isdelegator)
        {
            UserDto udto = new UserDto();
            udto.setFirstName("Fulvio");
            udto.setLastName("Rossi");
            udto.setFiscalCode("RSSFLV95C12H118C");
            udto.setPerson(true);
            udto.setEmail("fulvio.rossi@gmail.com");
            dto.delegator(udto);
        }    
        else
        {
            UserDto udto = new UserDto();
            udto.setFirstName("Fulvio");
            udto.setLastName("Verdi");
            udto.setFiscalCode("VRDFLV95C11H117C");
            udto.setPerson(true);
            udto.setEmail("fulvio.verdi@gmail.com");
            dto.delegate(udto);
        }
        dto.setMandateId(UUID.randomUUID().toString());
        dto.setVerificationCode("12345");
        dto.setVisibilityIds(new ArrayList<>());
        
        return dto;
    }

}
