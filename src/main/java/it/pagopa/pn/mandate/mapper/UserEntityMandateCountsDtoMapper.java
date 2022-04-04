package it.pagopa.pn.mandate.mapper;

import org.springframework.stereotype.Component;

import it.pagopa.pn.mandate.middleware.db.entities.UserEntity;
import it.pagopa.pn.mandate.rest.mandate.v1.dto.MandateCountsDto;
import reactor.core.publisher.Mono;

@Component
public class UserEntityMandateCountsDtoMapper implements BaseMapperInterface<MandateCountsDto, UserEntity> {
 
    private UserEntityMandateCountsDtoMapper(){
        super();
    }


    public static UserEntityMandateCountsDtoMapper Builder() { return new UserEntityMandateCountsDtoMapper(); }        

    @Override
    public Mono<UserEntity> toMonoEntity(Mono<MandateCountsDto> dto) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Mono<MandateCountsDto> toMonoDto(Mono<UserEntity> entity) {
        return entity.flatMap(ent -> {
            return Mono.just(toDto(ent));
        });
    }


    @Override
    public UserEntity toEntity(MandateCountsDto source) {
        throw new UnsupportedOperationException();
    }


    @Override
    public MandateCountsDto toDto(UserEntity ent) {
        final MandateCountsDto mDto = new MandateCountsDto();
        mDto.setValue(ent == null?0:ent.getPendingcount());
        return mDto;
    }
}
