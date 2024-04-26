(ns graphql
  (:require ["@aws-sdk/client-dynamodb" :as ddb]
            ["@as-integrations/aws-lambda" :as lambda]
            ["@apollo/server" :as apollo]
            ["@aws-sdk/lib-dynamodb" :as lib-ddb]
            ["@redis/client" :as redis]
            [common.base.core :as core]
            [promesa.core :as p]
            [dynamodb-tools :as ddbt]
            [resolvers :as r]))


(def client (ddb/DynamoDBClient. {}))
(def q-client (redis/createClient {"url" "redis://response-queues-gwjcas.serverless.euw1.cache.amazonaws.com:6379"}))
(def doc-client (lib-ddb/DynamoDBDocumentClient. client))


(defn- subscribe-response [ch id ctx resolver]
  (p/let [events (atom [])]
    (js/console.log "Subscribing to response queue " id)
    (.subscribe q-client id
                (fn [msg _]
                  (let [v (js/JSON.parse msg)]
                    (js/console.log "Received message" v)
                    (swap! events conj v)
                    (p/let [res (resolver ctx @events)]
                      (when res
                        (.unsubscribe q-client id)
                        (p/put ch res))))))))

(defn write-command [command-name body ctx resolver]
  (p/let [id (core/uuid)
          command (lib-ddb/PutCommand. (clj->js {"TableName" "events"
                                                 "Item" {"event-id" {"S" id}
                                                         "correlation-id" {"S" id}
                                                         "topic" {"S" "commands"}
                                                         "type" {"S" command-name}
                                                         "body" {"S" (js/JSON.stringify body)}
                                                         "created" {"S" (.toISOString (js/Date.))}}}))
          ch (p/chan :buf 2)
          _ (subscribe-response ch id ctx resolver)
          command-response (.send doc-client command)
          _ (js/console.log "Sent command" command-response)
          res @(p/take ch 10000 :timeout)]
    res))

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

(js/console.log "Handler" handler)

(clj->js {:handler handler})
