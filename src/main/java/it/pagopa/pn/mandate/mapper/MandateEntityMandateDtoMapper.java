package it.pagopa.pn.mandate.mapper;


import it.pagopa.pn.mandate.middleware.db.entities.MandateEntity;
import it.pagopa.pn.mandate.rest.mandate.v1.dto.MandateDto;
import it.pagopa.pn.mandate.rest.mandate.v1.dto.OrganizationIdDto;
import it.pagopa.pn.mandate.rest.mandate.v1.dto.UserDto;
import it.pagopa.pn.mandate.utils.DateUtils;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class MandateEntityMandateDtoMapper implements BaseMapperInterface<MandateDto, MandateEntity> {


    private MandateEntityMandateDtoMapper(){
        super();
    }     

    @Override
    public MandateEntity toEntity(MandateDto dto) {
        final MandateEntity target = new MandateEntity();
        target.setMandateId(dto.getMandateId());
        if (dto.getDateto() != null)
            target.setValidto(DateUtils.atStartOfDay(DateUtils.parseDate(dto.getDateto()).toInstant()).plusDays(1).minusSeconds(1).toInstant());
        if (dto.getStatus() != null)
            target.setState(StatusEnumMapper.intValfromStatus(dto.getStatus()));
        target.setValidationcode(dto.getVerificationCode());
        target.setVisibilityIds(getOrgidsEntities(dto.getVisibilityIds()));
        if (dto.getDelegate() != null)
            target.setDelegateisperson(dto.getDelegate().getPerson());
        if (dto.getDelegator() != null)
            target.setDelegatorisperson(dto.getDelegator().getPerson());

        return target;
    }

    @Override
    public MandateDto toDto(MandateEntity entity) {
        final MandateDto target = new MandateDto();
        target.setMandateId(entity.getMandateId());
        target.setDatefrom(DateUtils.formatDate(entity.getValidfrom()));
        target.setDateto(DateUtils.formatDate(entity.getValidto()));
        target.setStatus(StatusEnumMapper.fromValue(entity.getState()));
        target.setVerificationCode(entity.getValidationcode());
        target.setVisibilityIds(getOrgidsDtos(entity.getVisibilityIds()));
        // popolo delegato e delegante, con l'unica informazioni che conosco (il fatto se è PF/PG)
        target.setDelegate(new UserDto());
        target.getDelegate().setPerson(entity.getDelegateisperson());
        target.setDelegator(new UserDto());
        target.getDelegator().setPerson(entity.getDelegatorisperson());

        return target;
    }


    private List<OrganizationIdDto> getOrgidsDtos(Set<String> ids){
        if (ids == null || ids.isEmpty())
            return new ArrayList<>();
        
        List<OrganizationIdDto> r = new ArrayList<>();
        ids.forEach(id -> {
            OrganizationIdDto oidto = new OrganizationIdDto();
            oidto.setUniqueIdentifier(id);
            r.add(oidto);
        });
        

        return r;
    }

    private Set<String> getOrgidsEntities(List<OrganizationIdDto> ids){
        if (ids == null || ids.isEmpty())
            return null;    // a dynamo non piace il set vuoto, vuole null
        
        Set<String> r = new HashSet<>();
        ids.forEach(oid -> r.add(oid.getUniqueIdentifier()));
        
        return r;
    }



}
