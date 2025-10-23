package it.pagopa.pn.ciechecker.generator.model;

import lombok.Data;

@Data
public class CieCaAndkey {

    private byte[] certPem; //Certificato.pem e EF.Cert_CIE.der
    private byte[] certKey;  //Certificato.K

}
