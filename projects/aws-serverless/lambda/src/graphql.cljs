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
;(def )
(def doc-client (lib-ddb/DynamoDBDocumentClient. client))


(defn- subscribe-response [ch id ctx resolver]
  (p/let [events (atom [])
          q-client (redis/createClient {"url" "rediss://response-queues-gwjcas.serverless.euw1.cache.amazonaws.com:6379"})
          _ (.on q-client "error" (fn [e] (js/console.error "Error" e)))
          x (.connect q-client)]
    (js/console.log "Subscribing to response queue " id " " q-client)
    (.subscribe q-client id
                (fn [msg _]
                  (let [v (js/JSON.parse msg)]
                    (js/console.log "Received message" v)
                    (swap! events conj v)
                    (p/let [res (resolver ctx @events)]
                      (when res
                        (.unsubscribe q-client id)
                        (p/resolve! ch res))))))))

(defn write-command [command-name body ctx resolver]
  (try
    (js/console.log "Starting command" command-name body)
    (let [ch (p/deferred)]
      (p/let [id (core/uuid)
              _ (js/console.log "Writing command" id command-name body)
              content (clj->js {"TableName" "events"
                                "Item" {"event-id" {"S" id}
                                        "correlation-id" {"S" id}
                                        "topic" {"S" "commands"}
                                        "type" {"S" command-name}
                                        "body" {"S" (js/JSON.stringify body)}
                                        "created" {"S" (.toISOString (js/Date.))}}})
              _ (js/console.log "Content" content)
              command (lib-ddb/PutCommand. content)
              _ (js/console.log "Command" command)
              _ (subscribe-response ch id ctx resolver)
              _ (js/console.log "Subscribed to response queue" id)
              _ (js/console.log "Sending command" command)
              command-response (.send doc-client command)
              _ (js/console.log "Sent command" command-response)
              res (deref ch 10000 :timeout)]
        res))
    (catch js/Error e
      (js/console.error "Error writing command" e)
      (js/console.trace e)
      (.toString e))))

(def type-defs
  "type Customer {
     id: ID!
     email: String
     invited: Boolean
   }
   type Query {
     Customer(id: ID!): Customer
   }
   type Mutation {
     InviteCustomer(email: String): Customer
   }
   schema {
     query: Query
     mutation: Mutation
   }")
(def resolvers
  {:Query {:Customer (fn [c a v]
                       (js/console.log c a v)
                       {})}
   :Mutation {:InviteCustomer (fn [c a v]
                                (js/console.log c a v)
                                (write-command "InviteCustomer" {:email (-> a js->clj (get "email"))}
                                               {:db client}
                                               (r/invite-responser
                                                (fn [db id] (let [cmd (ddbt/create-get-item-command "customers" id)]
                                                              (-> (.send db cmd) ddbt/result-item))))))}})

(def server (apollo/ApolloServer. (clj->js {"typeDefs" type-defs
                                            "resolvers" resolvers})))

(def handler (lambda/startServerAndCreateLambdaHandler server (.createAPIGatewayProxyEventRequestHandler lambda/handlers)
                                                       {"middleware" [(fn [e] (js/console.log "Event" e) e)]}))

(js/console.log "Handler" handler)

(clj->js {:handler handler})
