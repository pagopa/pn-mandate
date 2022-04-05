package it.pagopa.pn.mandate.mapper;
 
import reactor.core.publisher.Mono;

public interface BaseMapperInterface<T,S> {
    S toEntity(T source);
    T toDto(S source);
}
