const { handler: eventHandler } = require('./src/app/eventHandler');

/**
 * AWS Lambda function entry point.
 *
 * @param {object} event - The event object from the trigger (SSM Parameter change).
 * @param {object} context - The Lambda context object.
 * @returns {Promise<object>} The response object.
 */
exports.handler = async (event, context) => {
  console.log('SSM Parameter change event received:', JSON.stringify(event, null, 2));
  return eventHandler(event, context);
};