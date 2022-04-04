package it.pagopa.pn.mandate.mapper;
 
import reactor.core.publisher.Mono;

public interface BaseMapperInterface<T,S> {
    public Mono<S> toMonoEntity(Mono<T> source);
    public Mono<T> toMonoDto(Mono<S> source);   
    public S toEntity(T source);
    public T toDto(S source);   
}
