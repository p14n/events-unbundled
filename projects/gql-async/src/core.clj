(ns core
  (:require [async.core :as ac]
            [clojure.core.async :as a]
            [handlers :as h]
            [projectors :as p]
            [resolvers :as r]
            [common.core :as c]
            [bff.cache :as bff]
            [aleph.http :as http]
            [bff.graphql :as gql]))


(def schema
  '{:objects

    {:Customer {:fields
                {:id {:type ID}
                 :email {:type String}
                 :invited {:type Boolean}}}}
    :mutations
    {:InviteCustomer {:type :Customer
                      :description "Invite a customer"
                      :args {:email {:type String}}}}})

;; create-graphql-resolver {:email dean@p14n.comc} :InviteCustomer
;; command-sender {:email dean@p14n.comc, :type :InviteCustomer}
;; Command sent  true {:email dean@p14n.comc, :type :InviteCustomer, :res-corr-id 582121}
;; {"handler":"bff.cache$responder@401a0f5d","logger.thread_name":"async-dispatch-4","level":"INFO","logger.name":"async.core","channel":"commands","logger.source":{"line":25,"column":13,"file":"async/core.cljc","namespace":"async.core"},"message":"Received event","event":{"email":"dean@p14n.comc","type":"InviteCustomer","res-corr-id":"582121"},"timestamp":"2023-09-20T07:29:37.322481Z"}
;; Responder received event {:email dean@p14n.comc, :type :InviteCustomer, :res-corr-id 582121} with id 582121
;; invite-response {"handler":"clojure.lang.AFunction$1@14815ca7","logger.thread_name":"async-dispatch-5","level":"INFO","logger.name":"async.core","channel":"commands","logger.source":{"line":25,"column":13,"file":"async/core.cljc","namespace":"async.core"},"message":"Received event","event":{"email":"dean@p14n.comc","type":"InviteCustomer","res-corr-id":"582121"},"timestamp":"2023-09-20T07:29:37.322500Z"}
;; [
;; {:email dean@p14n.comc, :type :InviteCustomer, :res-corr-id 582121}]
;; No id found
;; Resolver returned nil

(defn create-system [handlers resolvers]
  (fn [do-with-state]
    (with-open [channels (->> (conj (ac/get-all-channel-names handlers) :commands :notify)
                              ac/create-all-channels-closable)
                db (c/closeable (atom {}) #(reset! % {}))
                command-sender (c/closeable (bff/create-command-sender
                                             (c/map-command-type-to-resolver resolvers)
                                             (fn [cmd] (a/put! (:commands @channels) cmd
                                                               (fn [a] (println "Command sent " a cmd))))))
                http (http/start-server (gql/create-gql-handler schema resolvers @command-sender)
                                        {:port 8080})
                st (c/closeable {:channels @channels
                                 :command-sender @command-sender
                                 :http http
                                 :notify-ch (fn [ev res]
                                              (a/put! (:notify @channels) (assoc res :res-corr-id (:res-corr-id ev)))
                                              nil)
                                 :db @db})
                system (ac/start-system @st handlers @channels bff/responder)]
      (do-with-state @st))))

(def with-system
  (create-system [h/invite-customer
                  p/project-customer-to-simple-db]
                 [r/invite-response]))

(defonce state (atom nil))
(defonce instance (atom (future ::never-run)))

(defonce start (c/start-fn instance #(with-system (c/publishing-state c/forever state))))
(defonce stop (c/stop-fn instance))

