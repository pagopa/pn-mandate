// keep this import
const { handler: eventHandler } = require('./src/app/eventHandler');

/**
 * AWS Lambda function entry point.
 *
 * @param {object} event - The event object from the trigger (e.g., EventBridge).
 * @param {object} context - The Lambda context object.
 * @returns {Promise<object>} The response object.
 */
exports.handler = async (event, context) => {
  console.log("CSCA Masterlist Sync Lambda invocation started", { event, context });

  try {
    const result = await eventHandler(event, context);
    console.log("CSCA Masterlist Sync Lambda invocation finished successfully", { result });
    return result;
  } catch (error) {
    console.error("An unhandled error occurred in the CSCA Masterlist Sync Lambda", {
      errorMessage: error.message,
      errorStack: error.stack,
    });
    throw error;
  }
};