package it.pagopa.pn.mandate.springbootcfg;


import it.pagopa.pn.commons.exceptions.PnErrorWebExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

@Configuration
@Order(-2)
public class PnErrorWebExceptionHandlerActivation extends PnErrorWebExceptionHandler {
}