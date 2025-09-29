const { expect } = require('chai');
const { mockClient } = require('aws-sdk-client-mock');
const { S3Client, PutObjectCommand } = require("@aws-sdk/client-s3");
const { SSMClient, GetParameterCommand, PutParameterCommand } = require("@aws-sdk/client-ssm");

// Import the module to be tested
const awsService = require('../app/awsService');

// Mock the AWS clients
const s3Mock = mockClient(S3Client);
const ssmMock = mockClient(SSMClient);

describe('awsService', () => {

  // Reset mocks before each test to ensure isolation
  beforeEach(() => {
    s3Mock.reset();
    ssmMock.reset();
  });

  describe('getSsmParameter', () => {
    const paramName = '/test/param';

    it('should return the parameter value if it exists', async () => {
      const paramValue = 'test-value';
      ssmMock.on(GetParameterCommand, { Name: paramName }).resolves({
        Parameter: { Value: paramValue },
      });

      const result = await awsService.getSsmParameter(paramName);
      expect(result).to.equal(paramValue);
    });

    it('should return null if the parameter is not found', async () => {
      const error = new Error("Parameter not found");
      error.name = 'ParameterNotFound';
      ssmMock.on(GetParameterCommand, { Name: paramName }).rejects(error);

      const result = await awsService.getSsmParameter(paramName);
      expect(result).to.be.null;
    });

    it('should re-throw other AWS SDK errors naturally', async () => {
      const genericError = new Error("Some other AWS error");
      genericError.name = 'AccessDenied';
      ssmMock.on(GetParameterCommand, { Name: paramName }).rejects(genericError);

      try {
        await awsService.getSsmParameter(paramName);
        expect.fail('Expected an error to be thrown');
      } catch (error) {
        expect(error.message).to.equal("Some other AWS error");
        expect(error.name).to.equal("AccessDenied");
      }
    });
  });

  describe('updateSsmParameter', () => {
    const paramName = '/test/param';
    const paramValue = 'new-value';

    it('should call PutParameterCommand with correct parameters', async () => {
      ssmMock.on(PutParameterCommand).resolves({});

      await awsService.updateSsmParameter(paramName, paramValue);

      // Verify that the command was called with the correct input
      const putParameterCalls = ssmMock.commandCalls(PutParameterCommand);
      expect(putParameterCalls).to.have.lengthOf(1);
      expect(putParameterCalls[0].args[0].input).to.deep.equal({
        Name: paramName,
        Value: paramValue,
        Type: 'String',
        Overwrite: true,
        Tier: 'Standard',
      });
    });

    it('should let AWS SDK errors propagate naturally', async () => {
      const updateError = new Error("Update failed");
      updateError.name = 'ThrottlingException';
      ssmMock.on(PutParameterCommand).rejects(updateError);

      try {
        await awsService.updateSsmParameter(paramName, paramValue);
        expect.fail('Expected an error to be thrown');
      } catch (error) {
        expect(error.message).to.equal("Update failed");
        expect(error.name).to.equal("ThrottlingException");
      }
    });
  });

  describe('saveFileToS3', () => {
    const bucketName = 'test-bucket';
    const objectKey = 'test/key.zip';
    const fileBuffer = Buffer.from('test content');

    it('should call PutObjectCommand with correct parameters', async () => {
      s3Mock.on(PutObjectCommand).resolves({});

      await awsService.saveFileToS3(bucketName, objectKey, fileBuffer);

      const putObjectCalls = s3Mock.commandCalls(PutObjectCommand);
      expect(putObjectCalls).to.have.lengthOf(1);
      expect(putObjectCalls[0].args[0].input).to.deep.equal({
        Bucket: bucketName,
        Key: objectKey,
        Body: fileBuffer,
        ContentType: 'application/zip'
      });
    });

    it('should let AWS SDK errors propagate naturally', async () => {
      const s3Error = new Error("S3 upload failed");
      s3Error.name = 'NoSuchBucket';
      s3Mock.on(PutObjectCommand).rejects(s3Error);

      try {
        await awsService.saveFileToS3(bucketName, objectKey, fileBuffer);
        expect.fail('Expected an error to be thrown');
      } catch (error) {
        expect(error.message).to.equal("S3 upload failed");
        expect(error.name).to.equal("NoSuchBucket");
      }
    });
  });
});