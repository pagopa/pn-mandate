const { createHash } = require('crypto');
const awsService = require('./awsService');

//env vars
const HTTP_TIMEOUT_MS = Number(process.env.HTTP_TIMEOUT_MS) || 60000;
const CSCA_MASTERLIST_URL = process.env.CSCA_MASTERLIST_URL;
const SHA256_SSM_PARAMETER_NAME = process.env.SHA256_SSM_PARAMETER_NAME;
const S3_BUCKET_NAME = process.env.S3_BUCKET_NAME;
const S3_OBJECT_KEY = process.env.S3_OBJECT_KEY;

/**
 * Downloads a file from the given URL with retry logic using native fetch.
 * @param {string} fileUrl - The URL to download from.
 * @returns {Promise<Buffer>} The downloaded file as a Buffer.
 */
async function downloadFile(fileUrl) {
  const retryableStatuses = [408, 429, 500, 502, 503, 504];
  const maxAttempts = 3;

  for (let attempt = 0; attempt < maxAttempts; attempt++) {
    try {
      const response = await fetch(fileUrl, {
        method: 'GET',
        signal: AbortSignal.timeout(HTTP_TIMEOUT_MS),
        headers: {
          'User-Agent': 'PagoPA-SEND-CscaMasterlistSync',
          'Accept': '*/*'
        }
      });

      if (!response.ok) {
        const errorBody = await response.text().catch(() => "Could not retrieve error body");
        const error = new Error(`HTTP error! status: ${response.status} for URL: ${fileUrl}. Body: ${errorBody}`);
        error.statusCode = response.status;
        
        if (attempt < maxAttempts - 1 && retryableStatuses.includes(response.status)) {
          const delay = 1000 * Math.pow(2, attempt) + Math.random() * 1000;
          console.log(`Attempt ${attempt + 1} failed with status ${response.status}, retrying in ${Math.round(delay)}ms`);
          await new Promise(resolve => setTimeout(resolve, delay));
          continue;
        }
        
        throw error;
      }

      return Buffer.from(await response.arrayBuffer());
    } catch (error) {
      //handle timeout and network errors
      const isTimeout = error.name === 'TimeoutError' || error.name === 'AbortError';
      const isNetworkError = error.cause?.code === 'ECONNRESET' || 
                             error.cause?.code === 'ENOTFOUND' || 
                             error.cause?.code === 'ECONNREFUSED' ||
                             error.code === 'ECONNRESET' || 
                             error.code === 'ENOTFOUND' || 
                             error.code === 'ECONNREFUSED';
      
      if (attempt < maxAttempts - 1 && (isTimeout || isNetworkError)) {
        const delay = 1000 * Math.pow(2, attempt) + Math.random() * 1000;
        console.log(`Attempt ${attempt + 1} failed with ${error.message}, retrying in ${Math.round(delay)}ms`);
        await new Promise(resolve => setTimeout(resolve, delay));
        continue;
      }
      
      throw error;
    }
  }

  throw new Error('Download failed after all retry attempts');
}

/**
 * Main handler for the Lambda function.
 * @param {object} event - The Lambda event object.
 * @param {object} context - The Lambda context object.
 * @returns {Promise<object>} Result object with status and file information.
 */
async function handler(event, context) {
  if (!CSCA_MASTERLIST_URL || !SHA256_SSM_PARAMETER_NAME || !S3_BUCKET_NAME || !S3_OBJECT_KEY) {
    throw new Error("Missing required environment variables: CSCA_MASTERLIST_URL, SHA256_SSM_PARAMETER_NAME, S3_BUCKET_NAME, or S3_OBJECT_KEY");
  }

  console.log("Starting direct file download and SHA256 verification");

  const fileBuffer = await downloadFile(CSCA_MASTERLIST_URL);
  const newSha256 = createHash('sha256').update(fileBuffer).digest('hex');
  
  console.log(`Downloaded file size: ${fileBuffer.length} bytes`);
  console.log(`Calculated SHA256 of downloaded file: ${newSha256}`);

  const storedSha256 = await awsService.getSsmParameter(SHA256_SSM_PARAMETER_NAME);
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

  await awsService.saveFileToS3(S3_BUCKET_NAME, S3_OBJECT_KEY, fileBuffer);

  await awsService.updateSsmParameter(SHA256_SSM_PARAMETER_NAME, newSha256);

  console.log("Successfully synchronized, verified, and stored the new CSCA masterlist.");
  return { 
    status: 'SUCCESS', 
    newSha256: newSha256,
    fileSize: fileBuffer.length
  };
}

module.exports = { handler };