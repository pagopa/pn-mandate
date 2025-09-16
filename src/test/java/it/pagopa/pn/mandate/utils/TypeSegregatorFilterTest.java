package it.pagopa.pn.mandate.utils;

import it.pagopa.pn.mandate.model.WorkFlowType;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TypeSegregatorFilterTest {

    @Test
    void testFromWorkflowTypeStandard() {
        assertEquals(TypeSegregatorFilter.STANDARD, TypeSegregatorFilter.fromWorkflowType(WorkFlowType.STANDARD));
    }

    @Test
    void testFromWorkflowTypeReverse() {
        assertEquals(TypeSegregatorFilter.STANDARD, TypeSegregatorFilter.fromWorkflowType(WorkFlowType.REVERSE));
    }

    @Test
    void testFromWorkflowTypeCie() {
        assertEquals(TypeSegregatorFilter.CIE, TypeSegregatorFilter.fromWorkflowType(WorkFlowType.CIE));
    }

    @Test
    void testFromWorkflowTypeNull() {
        assertEquals(TypeSegregatorFilter.STANDARD, TypeSegregatorFilter.fromWorkflowType(null));
    }

    @Test
    void testBuildExpressionStandard() {
        Map<String, AttributeValue> values = new HashMap<>();
        String expr = TypeSegregatorFilter.STANDARD.buildExpression(values);
        assertTrue(expr.contains("attribute_not_exists(s_workflowtype)"));
        assertTrue(expr.contains("s_workflowtype = :type0"));
        assertTrue(expr.contains("s_workflowtype = :type1"));
        assertEquals(2, values.size());
        assertEquals("STANDARD", values.get(":type0").s());
        assertEquals("REVERSE", values.get(":type1").s());
    }

    @Test
    void testBuildExpressionCie() {
        Map<String, AttributeValue> values = new HashMap<>();
        String expr = TypeSegregatorFilter.CIE.buildExpression(values);
        assertTrue(expr.contains("s_workflowtype = :type0"));
        assertEquals(1, values.size());
        assertEquals("CIE", values.get(":type0").s());
    }

    @Test
    void testBuildExpressionAll() {
        Map<String, AttributeValue> values = new HashMap<>();
        String expr = TypeSegregatorFilter.ALL.buildExpression(values);
        assertTrue(expr.contains("attribute_not_exists(s_workflowtype)"));
        assertTrue(expr.contains("s_workflowtype = :type0"));
        assertTrue(expr.contains("s_workflowtype = :type1"));
        assertTrue(expr.contains("s_workflowtype = :type2"));
        assertEquals(3, values.size());
        assertEquals("STANDARD", values.get(":type0").s());
        assertEquals("REVERSE", values.get(":type1").s());
        assertEquals("CIE", values.get(":type2").s());
    }

    @Test
    void testFromWorkflowTypeNullReturnsStandard() {
        assertEquals(TypeSegregatorFilter.STANDARD, TypeSegregatorFilter.fromWorkflowType(null));
    }

}
