(ns graphql
  (:require ["@as-integrations/aws-lambda" :as lambda]
            ["@apollo/server" :as apollo]
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
                       (let [{:keys [id]} (shell/js->kwclj a)]
                         (ddb/single-item-fetch shell/db-client "customers" id)))}
   :Mutation {:InviteCustomer (fn [c a v]
                                (js/console.log c a v)
                                (shell/write-command "InviteCustomer" (shell/js->kwclj a) {}
                                                     (r/invite-responser
                                                      (fn [db id] (ddb/single-item-fetch db "customers" id)))))}})

(def server (apollo/ApolloServer. (clj->js {"typeDefs" type-defs
                                            "resolvers" resolvers})))

(def handler (lambda/startServerAndCreateLambdaHandler server (.createAPIGatewayProxyEventRequestHandler lambda/handlers)
                                                       {"middleware" [(fn [e] (js/console.log "Event" e) e)]}))

(clj->js {:handler handler})
