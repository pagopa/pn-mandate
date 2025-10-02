package it.pagopa.pn.ciechecker.client.s3;

import java.io.InputStream;

public interface S3BucketClient {

  InputStream getObjectContent(String key);

}
