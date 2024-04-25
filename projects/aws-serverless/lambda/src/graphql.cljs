(ns graphql
  (:require ["@aws-sdk/client-dynamodb" :as ddb]
            ["@as-integrations/aws-lambda" :as lambda]
            ["@apollo/server" :as apollo]
            ["@aws-sdk/lib-dynamodb" :as lib-ddb]
            [common.base.core :as core]
            [promesa.core :as p]
            [promesa.exec.csp :as sp]
            [dynamodb-tools :as ddbt]
            [resolvers :as r]))


(def client (ddb/DynamoDBClient. {}))
(def doc-client (lib-ddb/DynamoDBDocumentClient. client))

(defn from-sqs [id])

(defn- get-response [ch id ctx resolver]
  (sp/go-loop [events []
               counter 10]
    (let [v (sp/take (from-sqs id) 1000 {})
          res (resolver ctx (conj events v))]
      (if (or res (zero? counter))
        (sp/put ch res)
        (recur (conj events v) (dec counter))))))


(defn write-command [command-name body ctx resolver]
  (let [id (core/uuid)
        command (lib-ddb/PutCommand. (clj->js {"TableName" "events"
                                               "Item" {"event-id" {"S" id}
                                                       "correlation-id" {"S" id}
                                                       "topic" {"S" "commands"}
                                                       "type" {"S" command-name}
                                                       "body" {"S" (js/JSON.stringify body)}
                                                       "created" {"S" (.toISOString (js/Date.))}}}))
        ch (sp/chan :buf 2)
        _ (get-response ch id resolver)
        response (.send doc-client command)
        res @(sp/take ch 10000 :timeout)]
    response))

(def type-defs
  "type Customer {
     id: ID!
     email: String
     invited: Boolean
   }
   type Mutation {
     InviteCustomer(email: String): Customer
   }")
(def resolvers
  {:Mutation {:InviteCustomer (fn [c a v]
                                (js/console.log c a v)
                                (write-command "InviteCustomer" {:email (-> a js->clj (get "email"))}
                                               {:db client}
                                               (r/invite-responser
                                                (fn [db id] (let [cmd (ddbt/create-get-item-command "customers" id)]
                                                              (-> (.send db cmd) p/resolve ddbt/result-item))))))}})

(def server (apollo/ApolloServer. (clj->js {"typeDefs" type-defs
                                            "resolvers" resolvers})))

(def handler (lambda/startServerAndCreateLambdaHandler server (lambda/createAPIGatewayProxyEventV2RequestHandler)))

#js {:handler handler}
;; import { ApolloServer } from '@apollo/server';
;; import { startServerAndCreateLambdaHandler, handlers } from '@as-integrations/aws-lambda';
;;  import { DynamoDBClient } from "@aws-sdk/client-dynamodb";
;; import { PutCommand, DynamoDBDocumentClient } from "@aws-sdk/lib-dynamodb";
;; import { uuid } from 'uuidv4';

;; const client = new DynamoDBClient({});
;; const docClient = DynamoDBDocumentClient.from(client);

;; export const writeCommand = async (commandName,body) => {
;;     const id = uuid();
;;     const command = new PutCommand({
;;       TableName: "events",
;;       Item: {
;;         "event-id": id,
;;         "correlation-id": id,
;;         "topic" : "commands",
;;         "type": commandName,
;;         "body": JSON.stringify(body),
;;         "created": new Date().toISOString(),
;;       },
;;     });

;;     const response = await docClient.send(command);
;;     console.log(response);
;;     return response;
;;   };

;; const typeDefs = `#graphql
;;   type Customer {
;;     id: ID!
;;     email: String
;;     invited: Boolean
;;   }
;;   type Mutation {
;;     InviteCustomer(email: String): Customer
;;   }
;; `;

;; const resolvers = {
;;   Mutation: {
;;     InviteCustomer: ({email}) => writeCommand("InviteCustomer", {email})
;;   },
;; };

;; const server = new ApolloServer({
;;   typeDefs,
;;   resolvers,
;; });

;; // This final export is important!

;; export const graphqlHandler = startServerAndCreateLambdaHandler(
;;   server,
;;   // We will be using the Proxy V2 handler
;;   handlers.createAPIGatewayProxyEventV2RequestHandler(),
;; );