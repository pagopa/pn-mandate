package it.pagopa.pn.ciechecker.model;

import lombok.Data;

import java.util.List;

@Data
public class CieMrtd {
    private byte[] sod;
    private byte[] dg1;
    private byte[] dg11;
    private List<byte[]> cscaAnchor;
}