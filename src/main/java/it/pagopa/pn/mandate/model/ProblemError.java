package it.pagopa.pn.mandate.model;


import java.net.URI;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import java.time.OffsetDateTime;
import javax.validation.Valid;
import javax.validation.constraints.*;


import java.util.*;
import javax.annotation.Generated;

/**
 * ProblemError
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", date = "2025-12-17T19:09:05.786013+01:00[Europe/Rome]")
@lombok.ToString
public class ProblemError   {

    @JsonProperty("code")
    private String code;

    @JsonProperty("element")
    private String element;

    @JsonProperty("detail")
    private String detail;

    public it.pagopa.pn.mandate.appio.generated.openapi.server.v1.dto.ProblemError code(String code) {
        this.code = code;
        return this;
    }

    /**
     * Internal code of the error, in human-readable format
     * @return code
     */
    @NotNull
    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public it.pagopa.pn.mandate.appio.generated.openapi.server.v1.dto.ProblemError element(String element) {
        this.element = element;
        return this;
    }

    /**
     * Parameter or request body field name for validation error
     * @return element
     */

    public String getElement() {
        return element;
    }

    public void setElement(String element) {
        this.element = element;
    }

    public it.pagopa.pn.mandate.appio.generated.openapi.server.v1.dto.ProblemError detail(String detail) {
        this.detail = detail;
        return this;
    }

    /**
     * A human readable explanation specific to this occurrence of the problem.
     * @return detail
     */
    @Size(max = 1024)
    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        it.pagopa.pn.mandate.appio.generated.openapi.server.v1.dto.ProblemError problemError = (it.pagopa.pn.mandate.appio.generated.openapi.server.v1.dto.ProblemError) o;
        return Objects.equals(this.code, problemError.code) &&
                Objects.equals(this.element, problemError.element) &&
                Objects.equals(this.detail, problemError.detail);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code, element, detail);
    }
}

