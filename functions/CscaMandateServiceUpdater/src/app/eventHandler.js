const ecsService = require('./ecsService');

// Expected environment variables
const ECS_CLUSTER_NAME = process.env.ECS_CLUSTER_NAME;
const ECS_SERVICE_NAME = process.env.ECS_SERVICE_NAME;

/**
 * Main handler for the Lambda function.
 * Triggers ECS service redeployment when SSM parameter changes.
 * 
 * @param {object} event - The Lambda event object from SSM parameter change.
 * @param {object} context - The Lambda context object.
 * @returns {Promise<object>} Result object with status and service information.
 */
async function handler(event, context) {
  if (!ECS_CLUSTER_NAME || !ECS_SERVICE_NAME) {
    throw new Error('Missing required environment variables: ECS_CLUSTER_NAME or ECS_SERVICE_NAME');
  }

  console.log('Starting ECS service redeployment', {
    cluster: ECS_CLUSTER_NAME,
    service: ECS_SERVICE_NAME
  });

  const response = await ecsService.updateEcsService(ECS_CLUSTER_NAME, ECS_SERVICE_NAME);

  console.log('ECS service redeploy triggered successfully', {
    cluster: ECS_CLUSTER_NAME,
    service: ECS_SERVICE_NAME,
    serviceArn: response.service.serviceArn,
    deploymentId: response.service.deployments?.[0]?.id
  });

  return {
    statusCode: 200,
    body: {
      cluster: ECS_CLUSTER_NAME,
      service: ECS_SERVICE_NAME,
      serviceArn: response.service.serviceArn,
      message: 'ECS service redeploy triggered successfully'
    }
  };
}

module.exports = { handler };