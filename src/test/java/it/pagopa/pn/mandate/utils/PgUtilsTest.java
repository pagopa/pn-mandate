package it.pagopa.pn.mandate.utils;

import it.pagopa.pn.mandate.exceptions.PnForbiddenException;
import it.pagopa.pn.mandate.rest.mandate.v1.dto.CxTypeAuthFleet;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PgUtilsTest {

    @Test
    void testValidaAccessoPF() {
        StepVerifier.create(PgUtils.validaAccessoOnlyAdmin(CxTypeAuthFleet.PF, null, null))
                .expectNextCount(1)
                .expectComplete();
        StepVerifier.create(PgUtils.validaAccessoOnlyAdmin(null, null, null))
                .expectNextCount(1)
                .expectComplete();
    }

    @Test
    void testValidaAccessoPG() {
        StepVerifier.create(PgUtils.validaAccessoOnlyAdmin(CxTypeAuthFleet.PG, "admin", null))
                .expectNextCount(1)
                .expectComplete();
        StepVerifier.create(PgUtils.validaAccessoOnlyAdmin(CxTypeAuthFleet.PG, "admin", Collections.emptyList()))
                .expectNextCount(1)
                .expectComplete();
        StepVerifier.create(PgUtils.validaAccessoOnlyGroupAdmin(CxTypeAuthFleet.PG, "admin", List.of("")))
                .expectNextCount(1)
                .expectComplete();
    }

    @Test
    void testValidaAccessoPG_Failure() {
        StepVerifier.create(PgUtils.validaAccessoOnlyAdmin(CxTypeAuthFleet.PG, "operator", null))
                .expectError(PnForbiddenException.class)
                .verify();
        StepVerifier.create(PgUtils.validaAccessoOnlyAdmin(CxTypeAuthFleet.PG, "admin", List.of("")))
                .expectError(PnForbiddenException.class)
                .verify();
        StepVerifier.create(PgUtils.validaAccessoOnlyGroupAdmin(CxTypeAuthFleet.PG, "operator", List.of("")))
                .expectError(PnForbiddenException.class)
                .verify();
    }

    @Test
    void testBuildExpressionGroupFilter1() {
        Map<String, AttributeValue> attributeValue = new HashMap<>();
        String result = PgUtils.buildExpressionGroupFilter(List.of("G1"), attributeValue);
        assertEquals("( contains(groups,:group0) )", result);
        assertEquals(1, attributeValue.size());
    }

    @Test
    void testBuildExpressionGroupFilter2() {
        Map<String, AttributeValue> attributeValue = new HashMap<>();
        String result = PgUtils.buildExpressionGroupFilter(List.of("G1", "G2"), attributeValue);
        assertEquals("( contains(groups,:group0) OR contains(groups,:group1) )", result);
        assertEquals(2, attributeValue.size());
    }

}