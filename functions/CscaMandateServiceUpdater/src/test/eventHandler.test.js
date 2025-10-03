const { describe, it, beforeEach, afterEach } = require('mocha');
const { expect } = require('chai');
const proxyquire = require('proxyquire').noCallThru();
const sinon = require('sinon');
const path = require('path');

// Mock ECS service module
const ecsServiceMock = {
  updateEcsService: sinon.stub()
};

const eventHandlerPath = path.resolve(__dirname, '../app/eventHandler');

describe('eventHandler', () => {
  const originalEnv = process.env;
  let handler;

  beforeEach(() => {
    process.env = {
      ...originalEnv,
      ECS_CLUSTER_NAME: 'pn-mock-cluster',
      ECS_SERVICE_NAME: 'pn-mandate'
    };

    // Load handler with injected mocks
    const handlerModule = proxyquire(eventHandlerPath, {
      './ecsService': ecsServiceMock
    });
    handler = handlerModule.handler;
  });

  afterEach(() => {
    process.env = originalEnv;
    ecsServiceMock.updateEcsService.reset();
  });

  describe('successful scenarios', () => {
    it('should trigger ECS service redeployment successfully', async () => {
      const mockResponse = {
        service: {
          serviceArn: 'arn:aws:ecs:eu-south-1:123456789012:service/pn-mock-cluster/pn-mandate',
          serviceName: 'pn-mandate',
          clusterArn: 'arn:aws:ecs:eu-south-1:123456789012:cluster/pn-mock-cluster',
          status: 'ACTIVE',
          desiredCount: 2,
          deployments: [
            { id: 'ecs-svc/1234567890123456789' }
          ]
        }
      };

      ecsServiceMock.updateEcsService.withArgs('pn-mock-cluster', 'pn-mandate').resolves(mockResponse);

      const result = await handler({ detail: { name: '/test/parameter' } }, {});

      expect(result.statusCode).to.equal(200);
      expect(result.body.cluster).to.equal('pn-mock-cluster');
      expect(result.body.service).to.equal('pn-mandate');
      expect(result.body.serviceArn).to.equal(mockResponse.service.serviceArn);
      expect(result.body.message).to.equal('ECS service redeploy triggered successfully');
      expect(ecsServiceMock.updateEcsService.calledOnceWith('pn-mock-cluster', 'pn-mandate')).to.be.true;
    });

    it('should pass correct parameters to ECS service', async () => {
      const mockResponse = {
        service: {
          serviceArn: 'test-arn',
          deployments: []
        }
      };

      ecsServiceMock.updateEcsService.resolves(mockResponse);

      await handler({}, {});

      expect(ecsServiceMock.updateEcsService.calledOnce).to.be.true;
      const callArgs = ecsServiceMock.updateEcsService.getCall(0).args;
      expect(callArgs[0]).to.equal('pn-mock-cluster');
      expect(callArgs[1]).to.equal('pn-mandate');
    });
  });

  describe('error scenarios', () => {
    it('should throw error when ECS_CLUSTER_NAME is missing', async () => {
      delete process.env.ECS_CLUSTER_NAME;

      // Clear module cache
      Object.keys(require.cache).forEach(key => {
        if (key.includes('eventHandler')) {
          delete require.cache[key];
        }
      });

      try {
        const { handler: freshHandler } = proxyquire(eventHandlerPath, {
          './ecsService': ecsServiceMock
        });
        await freshHandler({}, {});
        expect.fail('Expected an error to be thrown');
      } catch (error) {
        expect(error.message).to.include('Missing required environment variables');
        expect(error.message).to.include('ECS_CLUSTER_NAME');
      }
    });

    it('should throw error when ECS_SERVICE_NAME is missing', async () => {
      delete process.env.ECS_SERVICE_NAME;

      // Clear module cache
      Object.keys(require.cache).forEach(key => {
        if (key.includes('eventHandler')) {
          delete require.cache[key];
        }
      });

      try {
        const { handler: freshHandler } = proxyquire(eventHandlerPath, {
          './ecsService': ecsServiceMock
        });
        await freshHandler({}, {});
        expect.fail('Expected an error to be thrown');
      } catch (error) {
        expect(error.message).to.include('Missing required environment variables');
        expect(error.message).to.include('ECS_SERVICE_NAME');
      }
    });

    it('should propagate ECS service errors', async () => {
      ecsServiceMock.updateEcsService.rejects(new Error('ECS service update failed'));

      try {
        await handler({}, {});
        expect.fail('Expected an error to be thrown');
      } catch (error) {
        expect(error.message).to.equal('ECS service update failed');
      }
    });

    it('should propagate AccessDeniedException errors', async () => {
      const accessDeniedError = new Error('User is not authorized to perform: ecs:UpdateService');
      accessDeniedError.name = 'AccessDeniedException';
      
      ecsServiceMock.updateEcsService.rejects(accessDeniedError);

      try {
        await handler({}, {});
        expect.fail('Expected an error to be thrown');
      } catch (error) {
        expect(error.name).to.equal('AccessDeniedException');
        expect(error.message).to.include('not authorized');
      }
    });
  });
});