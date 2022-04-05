package it.pagopa.pn.mandate.services;


import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
 

import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ServerWebExchange;

import it.pagopa.pn.mandate.rest.utils.ExceptionHelper;
import reactor.util.annotation.NonNull;
import reactor.util.annotation.NonNullApi;

@Configuration
@Order(-2)
@Slf4j
public class GlobalErrorHandler implements ErrorWebExceptionHandler {

  private final ObjectMapper objectMapper;

  public GlobalErrorHandler(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  @NonNull
  public Mono<Void> handle(@NonNull ServerWebExchange serverWebExchange, @NonNull Throwable throwable) {
    if (log.isErrorEnabled())
      log.error("exception catched", throwable);

    DataBufferFactory bufferFactory = serverWebExchange.getResponse().bufferFactory();
      serverWebExchange.getResponse().setStatusCode(HttpStatus.BAD_REQUEST);
      DataBuffer dataBuffer;
      try {
        dataBuffer = bufferFactory.wrap(objectMapper.writeValueAsBytes(ExceptionHelper.handleException(throwable, HttpStatus.BAD_REQUEST, "")));
      } catch (JsonProcessingException e) {
        dataBuffer = bufferFactory.wrap("".getBytes());
      }
      serverWebExchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
      return serverWebExchange.getResponse().writeWith(Mono.just(dataBuffer));
    

  } 

}