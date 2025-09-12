package it.pagopa.pn.ciechecker.model;

import lombok.Data;

import java.util.List;

@Data
public class CieIas {
    private byte[] sod;
    private byte[] nis;
    private List<byte[]> cscaAnchor;
}