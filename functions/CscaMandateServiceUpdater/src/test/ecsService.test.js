const { describe, it, beforeEach, afterEach } = require('mocha');
const { expect } = require('chai');
const { mockClient } = require('aws-sdk-client-mock');
const { ECSClient, UpdateServiceCommand } = require('@aws-sdk/client-ecs');
const { updateEcsService } = require('../app/ecsService');

const ecsMock = mockClient(ECSClient);

describe('ecsService', () => {
  beforeEach(() => {
    ecsMock.reset();
  });

  describe('updateEcsService', () => {
    it('should call ECS UpdateService with correct parameters', async () => {
      const mockResponse = {
        service: {
          serviceArn: 'arn:aws:ecs:eu-south-1:123456789012:service/pn-mock-cluster/pn-mandate',
          serviceName: 'pn-mandate',
          status: 'ACTIVE',
          desiredCount: 2,
          deployments: [
            { id: 'ecs-svc/1234567890123456789' }
          ]
        }
      };

      ecsMock.on(UpdateServiceCommand).resolves(mockResponse);

      const result = await updateEcsService('pn-mock-cluster', 'pn-mandate');

      expect(result).to.deep.equal(mockResponse);

      const calls = ecsMock.commandCalls(UpdateServiceCommand);
      expect(calls).to.have.lengthOf(1);
      expect(calls[0].args[0].input).to.deep.equal({
        cluster: 'pn-mock-cluster',
        service: 'pn-mandate',
        forceNewDeployment: true
      });
    });

    it('should return ECS service response with deployment info', async () => {
      const mockResponse = {
        service: {
          serviceArn: 'arn:aws:ecs:region:account:service/cluster/service',
          status: 'ACTIVE',
          desiredCount: 3,
          deployments: [
            { 
              id: 'ecs-svc/123',
              status: 'PRIMARY',
              desiredCount: 3
            }
          ]
        }
      };

      ecsMock.on(UpdateServiceCommand).resolves(mockResponse);

      const result = await updateEcsService('test-cluster', 'test-service');

      expect(result.service.serviceArn).to.equal('arn:aws:ecs:region:account:service/cluster/service');
      expect(result.service.deployments).to.have.lengthOf(1);
      expect(result.service.deployments[0].id).to.equal('ecs-svc/123');
    });

    it('should propagate ServiceNotFoundException', async () => {
      const ecsError = new Error('Service not found');
      ecsError.name = 'ServiceNotFoundException';
      
      ecsMock.on(UpdateServiceCommand).rejects(ecsError);

      try {
        await updateEcsService('pn-mock-cluster', 'non-existent-service');
        expect.fail('Should have thrown an error');
      } catch (error) {
        expect(error.message).to.equal('Service not found');
        expect(error.name).to.equal('ServiceNotFoundException');
      }
    });

    it('should propagate ThrottlingException', async () => {
      const throttlingError = new Error('Rate exceeded');
      throttlingError.name = 'ThrottlingException';
      
      ecsMock.on(UpdateServiceCommand).rejects(throttlingError);

      try {
        await updateEcsService('pn-mock-cluster', 'pn-mandate');
        expect.fail('Should have thrown an error');
      } catch (error) {
        expect(error.message).to.equal('Rate exceeded');
        expect(error.name).to.equal('ThrottlingException');
      }
    });

    it('should propagate AccessDeniedException', async () => {
      const accessError = new Error('User is not authorized to perform: ecs:UpdateService');
      accessError.name = 'AccessDeniedException';
      
      ecsMock.on(UpdateServiceCommand).rejects(accessError);

      try {
        await updateEcsService('pn-mock-cluster', 'pn-mandate');
        expect.fail('Should have thrown an error');
      } catch (error) {
        expect(error.message).to.include('not authorized');
        expect(error.name).to.equal('AccessDeniedException');
      }
    });

    it('should propagate ClusterNotFoundException', async () => {
      const clusterError = new Error('Cluster not found');
      clusterError.name = 'ClusterNotFoundException';
      
      ecsMock.on(UpdateServiceCommand).rejects(clusterError);

      try {
        await updateEcsService('non-existent-cluster', 'pn-mandate');
        expect.fail('Should have thrown an error');
      } catch (error) {
        expect(error.message).to.equal('Cluster not found');
        expect(error.name).to.equal('ClusterNotFoundException');
      }
    });

    it('should propagate generic ECS errors', async () => {
      const genericError = new Error('Internal service error');
      genericError.name = 'InternalServerException';
      
      ecsMock.on(UpdateServiceCommand).rejects(genericError);

      try {
        await updateEcsService('pn-mock-cluster', 'pn-mandate');
        expect.fail('Should have thrown an error');
      } catch (error) {
        expect(error.message).to.equal('Internal service error');
        expect(error.name).to.equal('InternalServerException');
      }
    });
  });
});