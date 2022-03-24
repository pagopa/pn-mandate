package it.pagopa.pn.mandate.mapper;


import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import it.pagopa.pn.mandate.middleware.db.MandateDao;
import it.pagopa.pn.mandate.middleware.db.entities.MandateEntity;
import it.pagopa.pn.mandate.rest.mandate.v1.dto.MandateDto;
import it.pagopa.pn.mandate.rest.mandate.v1.dto.OrganizationIdDto;
import it.pagopa.pn.mandate.rest.mandate.v1.dto.MandateDto.StatusEnum;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class MandateEntityMandateDtoMapper implements BaseMapperInterface<MandateDto, MandateEntity> {

    private MandateEntityMandateDtoMapper(){
        super();
    }     

    @Override
    public Flux<MandateEntity> toEntityList(Flux<MandateDto> source) {
        throw new UnsupportedOperationException();
    }


    @Override
    public Flux<MandateDto> toDtoList(Flux<MandateEntity> entities) {
        return entities.flatMap(ent -> toDto(Mono.just(ent)));  
    }

    @Override
    public Mono<MandateEntity> toEntity(Mono<MandateDto> source) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Mono<MandateDto> toDto(Mono<MandateEntity> source) {
        return source.flatMap(entity -> {
            final MandateDto target = new MandateDto();
            target.setMandateId(entity.getSk().replace(MandateDao.MANDATE_PREFIX, ""));
            target.setDatefrom(entity.getValidfrom());
            target.setDateto(entity.getValidto());
            target.setStatus(StatusEnum.fromValue(DelegationState.fromValue(entity.getState()).getValueConst()));
            target.setVerificationCode(entity.getValidationcode());
            target.setVisibilityIds(getOrgidsDtos(entity.getVisibilityIds()));
           
            return Mono.just(target);
        });
    } 

    private List<OrganizationIdDto> getOrgidsDtos(Set<String> ids){
        if (ids == null || ids.size() == 0)
            return new ArrayList<OrganizationIdDto>();
        
        List<OrganizationIdDto> r = new ArrayList<>();
        for (String id : ids) {
            OrganizationIdDto oidto = new OrganizationIdDto();
            oidto.setUniqueIdentifier(id);
            r.add(oidto);
        }

        return r;
    }


}
