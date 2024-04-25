import { ApolloServer } from '@apollo/server';
import { startServerAndCreateLambdaHandler, handlers } from '@as-integrations/aws-lambda';
import { DynamoDBClient } from "@aws-sdk/client-dynamodb";
import { PutCommand, DynamoDBDocumentClient } from "@aws-sdk/lib-dynamodb";
import { uuid } from 'uuidv4';

const client = new DynamoDBClient({});
const docClient = DynamoDBDocumentClient.from(client);

export const writeCommand = async (commandName,body) => {
    const id = uuid();
    const command = new PutCommand({
      TableName: "events",
      Item: {
        "event-id": id,
        "correlation-id": id,
        "topic" : "commands",
        "type": commandName,
        "body": JSON.stringify(body),
        "created": new Date().toISOString(),
      },
    });
  
    const response = await docClient.send(command);
    console.log(response);
    return response;
  };

const typeDefs = `#graphql
  type Customer {
    id: ID!
    email: String
    invited: Boolean
  }
  type Mutation {
    InviteCustomer(email: String): Customer
  }
`;

const resolvers = {
  Mutation: {
    InviteCustomer: ({email}) => writeCommand("InviteCustomer", {email})
  },
};

const server = new ApolloServer({
  typeDefs,
  resolvers,
});

// This final export is important!

export const graphqlHandler = startServerAndCreateLambdaHandler(
  server,
  // We will be using the Proxy V2 handler
  handlers.createAPIGatewayProxyEventV2RequestHandler(),
);