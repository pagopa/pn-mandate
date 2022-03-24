package it.pagopa.pn.mandate.mapper;


import java.util.ArrayList;
import org.springframework.stereotype.Component;

import it.pagopa.pn.mandate.middleware.db.MandateDao;
import it.pagopa.pn.mandate.middleware.db.entities.MandateEntity;
import it.pagopa.pn.mandate.rest.mandate.v1.dto.InternalMandateDto;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class MandateEntityInternalMandateDtoMapper implements BaseMapperInterface<InternalMandateDto, MandateEntity> {

    private MandateEntityInternalMandateDtoMapper(){
        super();
    }     

    @Override
    public Flux<MandateEntity> toEntityList(Flux<InternalMandateDto> source) {
        throw new UnsupportedOperationException();
    }


    @Override
    public Flux<InternalMandateDto> toDtoList(Flux<MandateEntity> entities) {
        return entities.flatMap(ent -> toDto(Mono.just(ent)));  
    }

    @Override
    public Mono<MandateEntity> toEntity(Mono<InternalMandateDto> source) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Mono<InternalMandateDto> toDto(Mono<MandateEntity> source) {
        return source.flatMap(entity -> {
            final InternalMandateDto target = new InternalMandateDto();
            target.setMandateId(entity.getSk().replace(MandateDao.MANDATE_PREFIX, ""));
            target.setDatefrom(entity.getValidfrom());
            target.setDateto(entity.getValidto());
            target.setVisibilityIds(new ArrayList<String>(entity.getVisibilityIds()));
           
            return Mono.just(target);
        });
    } 
 
}
