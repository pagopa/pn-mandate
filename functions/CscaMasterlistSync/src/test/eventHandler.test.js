const { expect } = require('chai');
const sinon = require('sinon');
const proxyquire = require('proxyquire').noCallThru();

let awsServiceMock;
let requestStub;

function loadHandler() {
  delete require.cache[require.resolve('../app/eventHandler')];
  return proxyquire('../app/eventHandler', {
    './awsService': awsServiceMock,
    'undici': {
      request: requestStub
    }
  });
}

describe('eventHandler', () => {
  let originalEnv;

  beforeEach(() => {
    originalEnv = { ...process.env };
    
    process.env.CSCA_MASTERLIST_URL = 'https://csca-ita.interno.gov.it/certificatiCSCA/IT_MasterListCSCA.zip';
    process.env.SHA256_SSM_PARAMETER_NAME = '/test/sha256';
    process.env.S3_BUCKET_NAME = 'test-bucket';
    process.env.S3_OBJECT_KEY = 'test/masterlist.zip';

    awsServiceMock = {
      getSsmParameter: sinon.stub(),
      updateSsmParameter: sinon.stub(),
      saveFileToS3: sinon.stub()
    };

    requestStub = sinon.stub();
  });

  afterEach(() => {
    process.env = originalEnv;
    sinon.restore();
  });

  describe('successful scenarios', () => {
    it('should return NOT_MODIFIED when SHA256 is unchanged', async () => {
      const fileContent = Buffer.from('existing content');
      const existingSha256 = '2c26b46b68ffc68ff99b453c1d30413413422d706483bfa0f98a5e886266e7ae';
      
      requestStub.resolves({
        statusCode: 200,
        body: {
          arrayBuffer: async () => fileContent.buffer.slice(fileContent.byteOffset, fileContent.byteOffset + fileContent.byteLength)
        }
      });

      awsServiceMock.getSsmParameter.withArgs('/test/sha256').resolves(existingSha256);

      const eventHandler = loadHandler();
      const result = await eventHandler.handler({}, {});

      expect(result.status).to.equal('NOT_MODIFIED');
      expect(result.sha256).to.equal(existingSha256);
      expect(result.fileSize).to.equal(fileContent.length);
      expect(awsServiceMock.updateSsmParameter.called).to.be.false;
      expect(awsServiceMock.saveFileToS3.called).to.be.false;
    });

    it('should download and save new file when SHA256 is different', async () => {
      const fileContent = Buffer.from('new file content');
      const oldSha256 = 'old_hash';
      const newSha256 = 'bdf12421e59b5b9f55adf0d5a0c01176ba9a6ccade28a5b38e8b0b7f96f5f60e';
      
      requestStub.resolves({
        statusCode: 200,
        body: {
          arrayBuffer: async () => fileContent.buffer.slice(fileContent.byteOffset, fileContent.byteOffset + fileContent.byteLength)
        }
      });

      awsServiceMock.getSsmParameter.withArgs('/test/sha256').resolves(oldSha256);
      awsServiceMock.updateSsmParameter.resolves();
      awsServiceMock.saveFileToS3.resolves();

      const eventHandler = loadHandler();
      const result = await eventHandler.handler({}, {});

      expect(result.status).to.equal('SUCCESS');
      expect(result.newSha256).to.equal(newSha256);
      expect(result.fileSize).to.equal(fileContent.length);
      expect(awsServiceMock.saveFileToS3.calledOnce).to.be.true;
      expect(awsServiceMock.updateSsmParameter.calledOnceWith('/test/sha256', newSha256)).to.be.true;
    });

    it('should handle first run when no previous SHA256 exists', async () => {
      const fileContent = Buffer.from('first download content');
      const newSha256 = 'e258d248fda94c63753607f7c4494ee0fcbe92f1a76bfdac795c9d84101eb317';
      
      requestStub.resolves({
        statusCode: 200,
        body: {
          arrayBuffer: async () => fileContent.buffer.slice(fileContent.byteOffset, fileContent.byteOffset + fileContent.byteLength)
        }
      });

      awsServiceMock.getSsmParameter.withArgs('/test/sha256').resolves(null);
      awsServiceMock.updateSsmParameter.resolves();
      awsServiceMock.saveFileToS3.resolves();

      const eventHandler = loadHandler();
      const result = await eventHandler.handler({}, {});

      expect(result.status).to.equal('SUCCESS');
      expect(result.newSha256).to.equal(newSha256);
      expect(result.fileSize).to.equal(fileContent.length);
      expect(awsServiceMock.saveFileToS3.calledOnce).to.be.true;
      expect(awsServiceMock.updateSsmParameter.calledOnceWith('/test/sha256', newSha256)).to.be.true;
    });
  });

  describe('error scenarios', () => {
    it('should throw error when required environment variables are missing', async () => {
      delete process.env.CSCA_MASTERLIST_URL;

      const eventHandler = loadHandler();

      try {
        await eventHandler.handler({}, {});
        expect.fail('Expected an error to be thrown');
      } catch (error) {
        expect(error.message).to.include('CSCA_MASTERLIST_URL');
      }
    });

    it('should throw error when download fails with non-retryable status', async () => {
      requestStub.resolves({
        statusCode: 404,
        body: {}
      });

      const eventHandler = loadHandler();

      try {
        await eventHandler.handler({}, {});
        expect.fail('Expected an error to be thrown');
      } catch (error) {
        expect(error.message).to.include("GET request failed with status code: 404");
        expect(requestStub.calledOnce).to.be.true;
      }
    });

    it('should propagate AWS service errors', async () => {
      const fileContent = Buffer.from('test content');
      
      requestStub.resolves({
        statusCode: 200,
        body: {
          arrayBuffer: async () => fileContent.buffer.slice(fileContent.byteOffset, fileContent.byteOffset + fileContent.byteLength)
        }
      });

      awsServiceMock.getSsmParameter.rejects(new Error('SSM access denied'));

      const eventHandler = loadHandler();

      try {
        await eventHandler.handler({}, {});
        expect.fail('Expected an error to be thrown');
      } catch (error) {
        expect(error.message).to.equal('SSM access denied');
      }
    });
  });

  describe('retry logic', () => {
    it('should retry on retryable status codes and succeed on second attempt', async () => {
      const fileContent = Buffer.from('success content');
      
      requestStub.onFirstCall().resolves({
        statusCode: 500,
        body: {}
      });
      
      requestStub.onSecondCall().resolves({
        statusCode: 200,
        body: {
          arrayBuffer: async () => fileContent.buffer.slice(fileContent.byteOffset, fileContent.byteOffset + fileContent.byteLength)
        }
      });

      awsServiceMock.getSsmParameter.withArgs('/test/sha256').resolves(null);
      awsServiceMock.updateSsmParameter.resolves();
      awsServiceMock.saveFileToS3.resolves();

      const eventHandler = loadHandler();
      const result = await eventHandler.handler({}, {});

      expect(result.status).to.equal('SUCCESS');
      expect(requestStub.calledTwice).to.be.true;
    });

    it('should retry on network errors and succeed on third attempt', async () => {
      const fileContent = Buffer.from('success content');
      const networkError = new Error('Connection timeout');
      networkError.code = 'ETIMEDOUT';
      
      requestStub.onFirstCall().rejects(networkError);
      requestStub.onSecondCall().rejects(networkError);
      requestStub.onThirdCall().resolves({
        statusCode: 200,
        body: {
          arrayBuffer: async () => fileContent.buffer.slice(fileContent.byteOffset, fileContent.byteOffset + fileContent.byteLength)
        }
      });

      awsServiceMock.getSsmParameter.withArgs('/test/sha256').resolves(null);
      awsServiceMock.updateSsmParameter.resolves();
      awsServiceMock.saveFileToS3.resolves();

      const eventHandler = loadHandler();
      const result = await eventHandler.handler({}, {});

      expect(result.status).to.equal('SUCCESS');
      expect(requestStub.calledThrice).to.be.true;
    });

    it('should not retry on non-retryable status codes', async () => {
      requestStub.resolves({
        statusCode: 401,
        body: {}
      });

      const eventHandler = loadHandler();

      try {
        await eventHandler.handler({}, {});
        expect.fail('Expected an error to be thrown');
      } catch (error) {
        expect(error.message).to.include("GET request failed with status code: 401");
        expect(requestStub.calledOnce).to.be.true;
      }
    });

    it('should not retry on non-retryable network errors', async () => {
      const nonRetryableError = new Error('Invalid URL');
      nonRetryableError.code = 'ERR_INVALID_URL';
      
      requestStub.rejects(nonRetryableError);

      const eventHandler = loadHandler();

      try {
        await eventHandler.handler({}, {});
        expect.fail('Expected an error to be thrown');
      } catch (error) {
        expect(error.message).to.equal('Invalid URL');
        expect(requestStub.calledOnce).to.be.true;
      }
    });

    it('should fail after maximum retry attempts with retryable errors', async () => {
      const networkError = new Error('Connection reset');
      networkError.code = 'ECONNRESET';
      
      requestStub.rejects(networkError);

      const eventHandler = loadHandler();

      try {
        await eventHandler.handler({}, {});
        expect.fail('Expected an error to be thrown');
      } catch (error) {
        expect(error.code).to.equal('ECONNRESET');
        expect(requestStub.calledThrice).to.be.true;
      }
    });

    it('should fail after maximum retry attempts with retryable status codes', async () => {
      requestStub.resolves({
        statusCode: 503,
        body: {}
      });

      const eventHandler = loadHandler();

      try {
        await eventHandler.handler({}, {});
        expect.fail('Expected an error to be thrown');
      } catch (error) {
        expect(error.message).to.include('GET request failed with status code: 503');
        expect(requestStub.calledThrice).to.be.true;
      }
    });
  });
});