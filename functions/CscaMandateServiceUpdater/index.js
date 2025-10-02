const { updateEcsService } = require('./src/app/ecsService');

exports.handler = async (event) => {
  console.log('SSM Parameter change event received:', JSON.stringify(event, null, 2));
  return await updateEcsService();
};