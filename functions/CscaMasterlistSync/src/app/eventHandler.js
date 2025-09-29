const { request } = require('undici');
const { createHash } = require('crypto');
const env = require('env-var');
const awsService = require('./awsService');

function loadConfig() {
  return {
    CSCA_MASTERLIST_URL: env.get('CSCA_MASTERLIST_URL').required().asUrlString(),
    SHA256_SSM_PARAMETER_NAME: env.get('SHA256_SSM_PARAMETER_NAME').required().asString(),
    S3_BUCKET_NAME: env.get('S3_BUCKET_NAME').required().asString(),
    S3_OBJECT_KEY: env.get('S3_OBJECT_KEY').required().asString()
  };
}

async function downloadFile(fileUrl) {
  const retryableStatuses = [408, 429, 500, 502, 503, 504];
  const retryableErrors = ['ETIMEDOUT', 'ECONNRESET', 'ENOTFOUND', 'ECONNREFUSED'];

  let attempt = 0;
  const maxAttempts = 3;

  while (attempt < maxAttempts) {
    try {
      const { body, statusCode } = await request(fileUrl, {
        method: 'GET',
        headersTimeout: 10000,
        bodyTimeout: 60000,
        headers: {
          'User-Agent': 'PagoPA-SEND-CscaMasterlistSync'
        }
      });

      if (statusCode < 200 || statusCode >= 300) {
        const error = new Error(`GET request failed with status code: ${statusCode}`);
        error.statusCode = statusCode;
        
        if (attempt < maxAttempts - 1 && retryableStatuses.includes(statusCode)) {
          const delay = 1000 * Math.pow(2, attempt) + Math.random() * 1000;
          console.log(`Attempt ${attempt + 1} failed with status ${statusCode}, retrying in ${Math.round(delay)}ms`);
          await new Promise(resolve => setTimeout(resolve, delay));
          attempt++;
          continue;
        }
        
        throw error;
      }

      return Buffer.from(await body.arrayBuffer());
    } catch (error) {
      const isRetryableError = retryableErrors.some(code => error.code === code || error.message.includes(code));
      
      if (attempt < maxAttempts - 1 && isRetryableError) {
        const delay = 1000 * Math.pow(2, attempt) + Math.random() * 1000;
        console.log(`Attempt ${attempt + 1} failed with ${error.message}, retrying in ${Math.round(delay)}ms`);
        await new Promise(resolve => setTimeout(resolve, delay));
        attempt++;
        continue;
      }
      
      throw error;
    }
  }

  throw new Error('Download failed after all retry attempts');
}

async function handler(event, context) {
  const config = loadConfig();

  console.log("Starting direct file download and SHA256 verification");

  const fileBuffer = await downloadFile(config.CSCA_MASTERLIST_URL);
  const newSha256 = createHash('sha256').update(fileBuffer).digest('hex');
  
  console.log(`Downloaded file size: ${fileBuffer.length} bytes`);
  console.log(`Calculated SHA256 of downloaded file: ${newSha256}`);

  const storedSha256 = await awsService.getSsmParameter(config.SHA256_SSM_PARAMETER_NAME);
  console.log(`Stored SHA256 from SSM: ${storedSha256}`);

  if (storedSha256 && storedSha256 === newSha256) {
    console.log("File content is identical (SHA256 match). No update needed.");
    return { 
      status: 'NOT_MODIFIED', 
      sha256: newSha256,
      fileSize: fileBuffer.length 
    };
  }

  console.log("File content has changed or is new. Updating S3 and SSM.");

  await awsService.saveFileToS3(config.S3_BUCKET_NAME, config.S3_OBJECT_KEY, fileBuffer);

  await awsService.updateSsmParameter(config.SHA256_SSM_PARAMETER_NAME, newSha256);

  console.log("Successfully synchronized, verified, and stored the new CSCA masterlist.");
  return { 
    status: 'SUCCESS', 
    newSha256: newSha256,
    fileSize: fileBuffer.length
  };
}

module.exports = { handler };