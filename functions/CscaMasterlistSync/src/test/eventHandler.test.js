const { expect } = require('chai');
const sinon = require('sinon');
const crypto = require('crypto');
const proxyquire = require('proxyquire').noCallThru();
const path = require('path');

// Mock undici request module
const undiciMock = {
  request: sinon.stub()
};

// Mock AWS service module
const awsServiceMock = {
  getSsmParameter: sinon.stub(),
  updateSsmParameter: sinon.stub(),
  saveFileToS3: sinon.stub()
};

const eventHandlerPath = path.resolve(__dirname, '../app/eventHandler');

// Load handler with injected mocks
const { handler } = proxyquire(eventHandlerPath, {
  'undici': undiciMock,
  './awsService': awsServiceMock
});

describe('eventHandler', () => {
  let originalEnv;

  beforeEach(() => {
    originalEnv = { ...process.env };
    process.env.CSCA_MASTERLIST_URL = 'https://example.com/masterlist.zip';
    process.env.SHA256_SSM_PARAMETER_NAME = '/test/sha256';
    process.env.S3_BUCKET_NAME = 'test-bucket';
    process.env.S3_OBJECT_KEY = 'test/masterlist.zip';
  });

  afterEach(() => {
    process.env = originalEnv;
    sinon.restore();
    undiciMock.request.reset();
    awsServiceMock.getSsmParameter.reset();
    awsServiceMock.updateSsmParameter.reset();
    awsServiceMock.saveFileToS3.reset();
  });

  describe('successful scenarios', () => {
    it('should return NOT_MODIFIED when SHA256 is unchanged', async () => {
      const fileContent = Buffer.from('existing content');
      const existingSha256 = crypto.createHash('sha256').update(fileContent).digest('hex');
      
      undiciMock.request.resolves({
        statusCode: 200,
        body: { 
          arrayBuffer: () => Promise.resolve(fileContent.buffer.slice(
            fileContent.byteOffset, 
            fileContent.byteOffset + fileContent.byteLength
          )) 
        }
      });

      awsServiceMock.getSsmParameter.withArgs('/test/sha256').resolves(existingSha256);

      const result = await handler({}, {});

      expect(result.status).to.equal('NOT_MODIFIED');
      expect(result.sha256).to.equal(existingSha256);
      expect(awsServiceMock.saveFileToS3.called).to.be.false;
      expect(awsServiceMock.updateSsmParameter.called).to.be.false;
    });

    it('should download and save new file when SHA256 is different', async () => {
      const fileContent = Buffer.from('new file content');
      const oldSha256 = 'old_hash_value';
      const newSha256 = crypto.createHash('sha256').update(fileContent).digest('hex');
      
      undiciMock.request.resolves({
        statusCode: 200,
        body: { 
          arrayBuffer: () => Promise.resolve(fileContent.buffer.slice(
            fileContent.byteOffset, 
            fileContent.byteOffset + fileContent.byteLength
          )) 
        }
      });

      awsServiceMock.getSsmParameter.withArgs('/test/sha256').resolves(oldSha256);
      awsServiceMock.saveFileToS3.resolves();
      awsServiceMock.updateSsmParameter.resolves();

      const result = await handler({}, {});

      expect(result.status).to.equal('SUCCESS');
      expect(result.newSha256).to.equal(newSha256);
      expect(awsServiceMock.saveFileToS3.calledOnce).to.be.true;
      expect(awsServiceMock.updateSsmParameter.calledOnceWith('/test/sha256', newSha256)).to.be.true;
    });

    it('should handle first run when no previous SHA256 exists', async () => {
      const fileContent = Buffer.from('first download content');
      const newSha256 = crypto.createHash('sha256').update(fileContent).digest('hex');
      
      undiciMock.request.resolves({
        statusCode: 200,
        body: { 
          arrayBuffer: () => Promise.resolve(fileContent.buffer.slice(
            fileContent.byteOffset, 
            fileContent.byteOffset + fileContent.byteLength
          )) 
        }
      });

      awsServiceMock.getSsmParameter.withArgs('/test/sha256').resolves(null);
      awsServiceMock.saveFileToS3.resolves();
      awsServiceMock.updateSsmParameter.resolves();

      const result = await handler({}, {});

      expect(result.status).to.equal('SUCCESS');
      expect(result.newSha256).to.equal(newSha256);
      expect(awsServiceMock.saveFileToS3.calledOnce).to.be.true;
      expect(awsServiceMock.updateSsmParameter.calledOnceWith('/test/sha256', newSha256)).to.be.true;
    });
  });

  describe('error scenarios', () => {
    it('should throw error when required environment variables are missing', async () => {
      // Clear module cache before removing env variable
      Object.keys(require.cache).forEach(key => {
        if (key.includes('eventHandler') || key.includes('env-var')) {
          delete require.cache[key];
        }
      });
      
      delete process.env.CSCA_MASTERLIST_URL;
      
      try {
        const { handler: freshHandler } = proxyquire(eventHandlerPath, {
          'undici': undiciMock,
          './awsService': awsServiceMock
        });
        await freshHandler({}, {});
        expect.fail('Expected an error to be thrown');
      } catch (error) {
        expect(error.message).to.include('CSCA_MASTERLIST_URL');
      }
    });

    it('should throw error when download fails with non-retryable status', async () => {
      undiciMock.request.resolves({ statusCode: 404, body: {} });

      try {
        await handler({}, {});
        expect.fail('Expected an error to be thrown');
      } catch (error) {
        expect(error.message).to.include("GET request failed with status code: 404");
        expect(undiciMock.request.calledOnce).to.be.true;
      }
    });

    it('should propagate AWS service errors', async () => {
      const fileContent = Buffer.from('test content');
      undiciMock.request.resolves({
        statusCode: 200,
        body: { 
          arrayBuffer: () => Promise.resolve(fileContent.buffer.slice(
            fileContent.byteOffset, 
            fileContent.byteOffset + fileContent.byteLength
          )) 
        }
      });

      awsServiceMock.getSsmParameter.rejects(new Error('SSM access denied'));

      try {
        await handler({}, {});
        expect.fail('Expected an error to be thrown');
      } catch (error) {
        expect(error.message).to.equal('SSM access denied');
      }
    });
  });

  describe('retry logic', () => {
    it('should retry on retryable status codes and succeed on second attempt', async () => {
      const fileContent = Buffer.from('success content');
      undiciMock.request.onFirstCall().resolves({ statusCode: 500, body: {} });
      undiciMock.request.onSecondCall().resolves({
        statusCode: 200,
        body: { 
          arrayBuffer: () => Promise.resolve(fileContent.buffer.slice(
            fileContent.byteOffset, 
            fileContent.byteOffset + fileContent.byteLength
          )) 
        }
      });

      awsServiceMock.getSsmParameter.resolves(null);
      awsServiceMock.updateSsmParameter.resolves();
      awsServiceMock.saveFileToS3.resolves();

      const result = await handler({}, {});

      expect(result.status).to.equal('SUCCESS');
      expect(undiciMock.request.calledTwice).to.be.true;
    });

    it('should fail after maximum retry attempts with retryable errors', async () => {
      const networkError = new Error('Connection reset');
      networkError.code = 'ECONNRESET';
      undiciMock.request.rejects(networkError);

      try {
        await handler({}, {});
        expect.fail('Expected an error to be thrown');
      } catch (error) {
        // Verify original error is thrown after max retries
        expect(error.message).to.equal('Connection reset');
        expect(undiciMock.request.calledThrice).to.be.true;
      }
    });
  });
});