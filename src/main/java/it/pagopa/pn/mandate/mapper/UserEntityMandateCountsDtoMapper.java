package it.pagopa.pn.mandate.mapper;

import org.springframework.stereotype.Component;

import it.pagopa.pn.mandate.middleware.db.entities.UserEntity;
import it.pagopa.pn.mandate.rest.mandate.v1.dto.MandateCountsDto;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class UserEntityMandateCountsDtoMapper implements BaseMapperInterface<MandateCountsDto, UserEntity> {
 
    private UserEntityMandateCountsDtoMapper(){
        super();
    }


    public static UserEntityMandateCountsDtoMapper Builder() { return new UserEntityMandateCountsDtoMapper(); }        

    @Override
    public Mono<UserEntity> toEntity(Mono<MandateCountsDto> dto) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Mono<MandateCountsDto> toDto(Mono<UserEntity> entity) {
        return entity.flatMap(ent -> {
            final MandateCountsDto mDto = new MandateCountsDto();
            mDto.setValue(ent == null?0:ent.getPendingcount());
            return Mono.just(mDto);
        });
    }


    @Override
    public Flux<UserEntity> toEntityList(Flux<MandateCountsDto> dtos) {
        throw new UnsupportedOperationException();
    }


    @Override
    public Flux<MandateCountsDto> toDtoList(Flux<UserEntity> entities) {
        throw new UnsupportedOperationException();
    } 
}
