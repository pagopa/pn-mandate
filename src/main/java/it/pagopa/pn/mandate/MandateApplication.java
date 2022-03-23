package it.pagopa.pn.mandate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;

@SpringBootApplication
@OpenAPIDefinition(info = @Info(title = "PN Mandate u-service", version = "1.0", description = "Documentation APIs v1.0"))
public class MandateApplication {

	public static void main(String[] args) {
		SpringApplication.run(MandateApplication.class, args);
	}

}
