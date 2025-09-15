package it.pagopa.pn.ciechecker.model;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Getter
@Setter
public class CieMrtd {
    private byte[] sod;
    private byte[] dg1;
    private byte[] dg11;
}