package it.pagopa.pn.mandate.services;


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

@Configuration
@Order(-2)
public class GlobalErrorHandler implements ErrorWebExceptionHandler {

  private ObjectMapper objectMapper;

  public GlobalErrorHandler(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public Mono<Void> handle(ServerWebExchange serverWebExchange, Throwable throwable) {

    DataBufferFactory bufferFactory = serverWebExchange.getResponse().bufferFactory();
      serverWebExchange.getResponse().setStatusCode(HttpStatus.BAD_REQUEST);
      DataBuffer dataBuffer = null;
      try {
        dataBuffer = bufferFactory.wrap(objectMapper.writeValueAsBytes(ExceptionHelper.handleException(throwable, HttpStatus.BAD_REQUEST, "")));
      } catch (JsonProcessingException e) {
        dataBuffer = bufferFactory.wrap("".getBytes());
      }
      serverWebExchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
      return serverWebExchange.getResponse().writeWith(Mono.just(dataBuffer));
    

  } 

}