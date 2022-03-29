package it.pagopa.pn.mandate.mapper;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface BaseMapperInterface<T,S> {
    public Mono<S> toEntity(Mono<T> source);
    public Mono<T> toDto(Mono<S> source);
    public Flux<S> toEntityList(Flux<T> source);
    public Flux<T> toDtoList(Flux<S> source);
   
}
