package it.pagopa.pn.ciechecker.model;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Data
@Getter
@Setter
public class CieMrtd {
    private byte[] sod;
    private byte[] dg1;
    private byte[] dg11;
    private List<byte[]> cscaAnchor;
}