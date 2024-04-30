(ns graphql
  (:require ["@as-integrations/aws-lambda" :as lambda]
            ["@apollo/server" :as apollo]
            [promesa.core :as p]
            [dynamodb-tools :as ddb]
            [resolvers :as r]
            [shell :as shell]))



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
                                (p/let [client (ddb/create-client)]
                                  (shell/write-command "InviteCustomer" {:email (-> a js->clj (get "email"))}
                                                       {:db client}
                                                       (r/invite-responser
                                                        (fn [db id] (let [cmd (ddb/create-get-item-command "customers" id)]
                                                                      (-> (.send db cmd) ddb/result-item)))))))}})

(def server (apollo/ApolloServer. (clj->js {"typeDefs" type-defs
                                            "resolvers" resolvers})))

(def handler (lambda/startServerAndCreateLambdaHandler server (.createAPIGatewayProxyEventRequestHandler lambda/handlers)
                                                       {"middleware" [(fn [e] (js/console.log "Event" e) e)]}))

(js/console.log "Handler" handler)

(clj->js {:handler handler})
