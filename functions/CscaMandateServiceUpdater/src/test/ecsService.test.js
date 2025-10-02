const { describe, it, beforeEach, afterEach } = require('mocha');
const { expect } = require('chai');
const { mockClient } = require('aws-sdk-client-mock');
const { ECSClient, UpdateServiceCommand } = require('@aws-sdk/client-ecs');
const { updateEcsService } = require('../app/ecsService');

const ecsMock = mockClient(ECSClient);

describe('ECS Service Updater', () => {
  const originalEnv = process.env;

  beforeEach(() => {
    ecsMock.reset();
    process.env = {
      ...originalEnv,
      ECS_CLUSTER_NAME: 'pn-mock-cluster',
      ECS_SERVICE_NAME: 'pn-mandate'
    };
  });

  afterEach(() => {
    process.env = originalEnv;
  });

  describe('updateEcsService', () => {
    it('should trigger ECS service redeploy successfully', async () => {
      const mockResponse = {
        service: {
          serviceArn: 'arn:aws:ecs:eu-south-1:123456789012:service/pn-mock-cluster/pn-mandate',
          serviceName: 'pn-mandate',
          clusterArn: 'arn:aws:ecs:eu-south-1:123456789012:cluster/pn-mock-cluster',
          deployments: [
            { id: 'ecs-svc/1234567890123456789' }
          ]
        }
      };

      ecsMock.on(UpdateServiceCommand).resolves(mockResponse);

      const result = await updateEcsService();

      expect(result.statusCode).to.equal(200);
      expect(result.body.cluster).to.equal('pn-mock-cluster');
      expect(result.body.service).to.equal('pn-mandate');
      expect(result.body.serviceArn).to.equal(mockResponse.service.serviceArn);
      expect(result.body.message).to.equal('ECS service redeploy triggered successfully');
    });

    it('should call UpdateServiceCommand with correct parameters', async () => {
      ecsMock.on(UpdateServiceCommand).resolves({
        service: { serviceArn: 'test-arn' }
      });

      await updateEcsService();

      const calls = ecsMock.commandCalls(UpdateServiceCommand);
      expect(calls).to.have.lengthOf(1);
      expect(calls[0].args[0].input).to.deep.equal({
        cluster: 'pn-mock-cluster',
        service: 'pn-mandate',
        forceNewDeployment: true
      });
    });

    it('should throw error when ECS_CLUSTER_NAME is missing', async () => {
      delete process.env.ECS_CLUSTER_NAME;

      try {
        await updateEcsService();
        expect.fail('Should have thrown an error');
      } catch (error) {
        expect(error.message).to.include('Missing required environment variables');
      }
    });

    it('should throw error when ECS_SERVICE_NAME is missing', async () => {
      delete process.env.ECS_SERVICE_NAME;

      try {
        await updateEcsService();
        expect.fail('Should have thrown an error');
      } catch (error) {
        expect(error.message).to.include('Missing required environment variables');
      }
    });

    it('should throw error when both environment variables are missing', async () => {
      delete process.env.ECS_CLUSTER_NAME;
      delete process.env.ECS_SERVICE_NAME;

      try {
        await updateEcsService();
        expect.fail('Should have thrown an error');
      } catch (error) {
        expect(error.message).to.include('Missing required environment variables');
      }
    });

    it('should propagate ECS API errors', async () => {
      const ecsError = new Error('Service not found');
      ecsError.name = 'ServiceNotFoundException';
      
      ecsMock.on(UpdateServiceCommand).rejects(ecsError);

      try {
        await updateEcsService();
        expect.fail('Should have thrown an error');
      } catch (error) {
        expect(error.message).to.equal('Service not found');
        expect(error.name).to.equal('ServiceNotFoundException');
      }
    });

    it('should handle throttling errors', async () => {
      const throttlingError = new Error('Rate exceeded');
      throttlingError.name = 'ThrottlingException';
      
      ecsMock.on(UpdateServiceCommand).rejects(throttlingError);

      try {
        await updateEcsService();
        expect.fail('Should have thrown an error');
      } catch (error) {
        expect(error.message).to.equal('Rate exceeded');
        expect(error.name).to.equal('ThrottlingException');
      }
    });

    it('should handle access denied errors', async () => {
      const accessError = new Error('User is not authorized to perform: ecs:UpdateService');
      accessError.name = 'AccessDeniedException';
      
      ecsMock.on(UpdateServiceCommand).rejects(accessError);

      try {
        await updateEcsService();
        expect.fail('Should have thrown an error');
      } catch (error) {
        expect(error.message).to.include('not authorized');
        expect(error.name).to.equal('AccessDeniedException');
      }
    });

    it('should handle cluster not found errors', async () => {
      const clusterError = new Error('Cluster not found');
      clusterError.name = 'ClusterNotFoundException';
      
      ecsMock.on(UpdateServiceCommand).rejects(clusterError);

      try {
        await updateEcsService();
        expect.fail('Should have thrown an error');
      } catch (error) {
        expect(error.message).to.equal('Cluster not found');
        expect(error.name).to.equal('ClusterNotFoundException');
      }
    });
  });
});