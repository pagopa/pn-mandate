const { ECSClient, UpdateServiceCommand } = require('@aws-sdk/client-ecs');

// Client initialized outside the handler
const ecsClient = new ECSClient();

/**
 * Triggers a forced redeployment of an ECS service.
 * 
 * @param {string} clusterName - The name of the ECS cluster.
 * @param {string} serviceName - The name of the ECS service.
 * @returns {Promise<object>} The ECS UpdateService response.
 */
async function updateEcsService(clusterName, serviceName) {
  const command = new UpdateServiceCommand({
    cluster: clusterName,
    service: serviceName,
    forceNewDeployment: true
  });

  const response = await ecsClient.send(command);
  
  console.log('ECS UpdateService command executed successfully', {
    serviceArn: response.service.serviceArn,
    status: response.service.status,
    desiredCount: response.service.desiredCount
  });

  return response;
}

module.exports = {
  updateEcsService
};