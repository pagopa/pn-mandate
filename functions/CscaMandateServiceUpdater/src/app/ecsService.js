const { ECSClient, UpdateServiceCommand } = require('@aws-sdk/client-ecs');

const ecsClient = new ECSClient();

const updateEcsService = async () => {
  const clusterName = process.env.ECS_CLUSTER_NAME;
  const serviceName = process.env.ECS_SERVICE_NAME;

  if (!clusterName || !serviceName) {
    throw new Error('Missing required environment variables: ECS_CLUSTER_NAME, ECS_SERVICE_NAME');
  }

  const command = new UpdateServiceCommand({
    cluster: clusterName,
    service: serviceName,
    forceNewDeployment: true
  });

  try {
    const response = await ecsClient.send(command);
    console.log('ECS service redeploy triggered successfully:', {
      cluster: clusterName,
      service: serviceName,
      serviceArn: response.service.serviceArn,
      deploymentId: response.service.deployments?.[0]?.id
    });
    
    return {
      statusCode: 200,
      body: {
        cluster: clusterName,
        service: serviceName,
        serviceArn: response.service.serviceArn,
        message: 'ECS service redeploy triggered successfully'
      }
    };
  } catch (error) {
    console.error('Failed to trigger ECS redeploy:', {
      cluster: clusterName,
      service: serviceName,
      error: error.message,
      errorCode: error.name
    });
    throw error;
  }
};

module.exports = { updateEcsService };