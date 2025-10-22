package it.pagopa.pn.ciechecker.model;

import lombok.Data;

@Data
public class CieCaAndkey {

    private byte[] certPem; //Certificato.pem e EF.Cert_CIE.der
    private byte[] certKey;  //Certificato.K

}
