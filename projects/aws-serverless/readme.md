## Serverless
This project is designed to cost nothing when not in use, taking advantage of AWS's serverless products:
* The core functions are deployed as lambdas
    * The handler and projector are their own lambdas
    * The graphql server is a lambda, and embeds the resolving logic
* Commands/events are written to DynamoDB
* DynamoDB streams feed new events to Event Bridge
* Event Bridge rules push the event to registered lambdas
* Elasticache serverless Redis pub/sub is used for transient messages back to the requestor (graphql)

Again, no changes were necessary to the underlying code.  While I used the patterns, I didnt use the protocols. [nbb](https://github.com/babashka/nbb) (which is used to run clojure on nodejs) doesn't support `deftype` so I didn't have much of a choice.

You can see the lambda's implemented in [nodejs/src/lambda_handlers.cljs](nodejs/src/lambda_handlers.cljs) and the graphql server set up in [nodejs/src/graphql.cljs](nodejs/src/graphql.cljs).

The project can be set up in AWS:
* Install [babashka](https://github.com/babashka/babashka), nodejs and [nbb](https://github.com/babashka/nbb).
* Set up AWS command line credentials and have the AWS VPC id you want to use handy
* From the aws-serverless project directory, run `bb tf apply`

### Challenges
* I thought I could use SQS to provide a transient message mechanism.  This is not possible as:
    * There is no way to create temporary queues to implement a req/res pattern
    * Without a dedicated queue any listener will steal all messages (it can return them but after a delay)
* The use of Elasticache (VPC) with Lambda and DynamoDB (public network) mandated moving the Lambda's into a VPC and using a VPC endpoint for DynamoDB.  This works fine, but was fiddly to get right.
* Lambdas relying on async actions are very difficult to debug when they go wrong

### Performance
The graphql query response is okay - roughly 100ms to start, read a value from DynamoDB and return the request.  Mutation response takes seconds due to the multiple messages via DynamoDB.  The timings from a typical (not cold) start look like this:
* Start request
* Lambda started and command sent (217ms)
    * Handler fires (517ms)
    * Projector fires (905ms)
* Waiting for all downstream results (2.413s)
* Load and return result (28ms)

The entire request took 2712ms, but only 1667ms was spent in the lambdas, so ~1 seconds has gone to message passing in DynamoDB/EventBridge/Elasticache.  I spent no time improving or analysing this performance; tracing would be needed to really understand where to make improvements.

