package it.pagopa.pn.mandate.mapper;


import java.util.ArrayList;
import org.springframework.stereotype.Component;

import it.pagopa.pn.mandate.middleware.db.entities.MandateEntity;
import it.pagopa.pn.mandate.rest.mandate.v1.dto.InternalMandateDto;
import reactor.core.publisher.Mono;

@Component
public class MandateEntityInternalMandateDtoMapper implements BaseMapperInterface<InternalMandateDto, MandateEntity> {

    private MandateEntityInternalMandateDtoMapper(){
        super();
    }     

    @Override
    public Mono<MandateEntity> toMonoEntity(Mono<InternalMandateDto> source) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Mono<InternalMandateDto> toMonoDto(Mono<MandateEntity> source) {
        return source.flatMap(entity -> {           
            return Mono.just(toDto(entity));
        });
    }

    @Override
    public MandateEntity toEntity(InternalMandateDto source) {
        throw new UnsupportedOperationException();
    }

    @Override
    public InternalMandateDto toDto(MandateEntity entity) {
        final InternalMandateDto target = new InternalMandateDto();
        target.setMandateId(entity.getId());
        target.setDatefrom(entity.getValidfrom());
        target.setDateto(entity.getValidto());
        target.setVisibilityIds(new ArrayList<String>(entity.getVisibilityIds()));
        return target;
    } 
 
}
