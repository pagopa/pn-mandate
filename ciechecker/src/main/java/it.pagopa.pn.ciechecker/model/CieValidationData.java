package it.pagopa.pn.ciechecker.model;

import lombok.Data;

@Data
public class CieValidationData {
    private CieIas cieIas;
    private CieMrtd cieMrtd;
    private String nonce;
    private byte[] signedNonce;
}