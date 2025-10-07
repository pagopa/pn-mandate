package it.pagopa.pn.ciechecker.client.s3;

import it.pagopa.pn.ciechecker.exception.CieCheckerException;

import java.io.InputStream;

public interface S3BucketClient {

  InputStream getObjectContent(String key);
  InputStream getFileInputStreamCscaAnchor(String cscaAnchorPathFileName) throws CieCheckerException;

}
