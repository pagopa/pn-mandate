package it.pagopa.pn.mandate.middleware.microservice;

import lombok.Data;

@Data
public class FakeKeyValue {
    private final String key;
    private final String value;
}