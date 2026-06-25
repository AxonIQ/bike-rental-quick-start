/**
 * Thrown on the dispatching side when Axon Server reports that a command handler completed
 * exceptionally (the `POST /v2/commands` call returned an error status).
 */
export class CommandExecutionException extends Error {
  constructor(
    readonly errorCode: string,
    message: string,
  ) {
    super(message);
    this.name = 'CommandExecutionException';
  }
}

/**
 * Thrown on the dispatching side when Axon Server reports that a query handler completed
 * exceptionally (the `POST /v2/queries` call returned an error status).
 */
export class QueryExecutionException extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'QueryExecutionException';
  }
}
