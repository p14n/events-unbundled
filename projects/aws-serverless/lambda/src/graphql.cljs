(ns graphql
  (:require ["@aws-sdk/client-dynamodb" :as ddb]
            ["@as-integrations/aws-lambda" :as lambda]
            ["@apollo/server" :as apollo]
            ["@aws-sdk/lib-dynamodb" :as lib-ddb]
            ["@redis/client" :as redis]
            [common.base.core :as core]
            [promesa.core :as p]
            [promesa.exec.csp :as sp]
            [dynamodb-tools :as ddbt]
            [resolvers :as r]))


(def client (ddb/DynamoDBClient. {}))
(def q-client (redis/createClient {"url" "redis://response-queues-gwjcas.serverless.euw1.cache.amazonaws.com:6379"}))
(def doc-client (lib-ddb/DynamoDBDocumentClient. client))

;; (defn from-sqs [id])

;; (defn- get-response [ch id ctx resolver]
;;   ;(sp/go-loop
;;   (loop [events []
;;          counter 10]
;;     (let [v (sp/take (from-sqs id) 1000 {})
;;           res (resolver ctx (conj events v))]
;;       (if (or res (zero? counter))
;;         (sp/put ch res)
;;         (recur (conj events v) (dec counter))))))

(defn- subscribe-response [ch id ctx resolver]
  ;(sp/let)
  (sp/let [events (atom [])]
    (.subscribe q-client id
                (fn [msg _]
                  (let [v (js/JSON.parse msg)]
                    (swap! events conj v)
                    (sp/let [res (resolver ctx @events)]
                      (when res
                        (.unsubscribe q-client id)
                        (sp/put ch res))))))))

(defn write-command [command-name body ctx resolver]
  ;(sp/let)
  (sp/let [id (core/uuid)
           command (lib-ddb/PutCommand. (clj->js {"TableName" "events"
                                                  "Item" {"event-id" {"S" id}
                                                          "correlation-id" {"S" id}
                                                          "topic" {"S" "commands"}
                                                          "type" {"S" command-name}
                                                          "body" {"S" (js/JSON.stringify body)}
                                                          "created" {"S" (.toISOString (js/Date.))}}}))
           ch (sp/chan :buf 2)
           _ (subscribe-response ch id ctx resolver)
           _ (.send doc-client command)
           res @(sp/take ch 10000 :timeout)]
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

#js {:handler handler}
