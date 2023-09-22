(ns core
  (:require [handlers :as h]
            [projectors :as p]
            [resolvers :as r]
            [common.core :as c]
            [bff.cache :as bff]
            [aleph.http :as http]
            [bff.graphql :as gql]
            [kafka.producer :as kp]
            [com.kroo.epilogue :as log]
            [kafka.core :as kc]
            [xt.core :as xt]))


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

(defn create-system [handlers resolvers]
  (fn [do-with-state]
    (try (with-open [producer-channels (-> handlers
                                           (c/get-all-channel-names)
                                           (conj :commands :notify)
                                           (kp/create-producer-channels))
                     db (xt/start-node (xt/node-properties "xtdb"))
                     command-sender (c/closeable :command-sender (bff/create-command-sender
                                                                  (c/map-command-type-to-resolver resolvers)
                                                                  (-> @producer-channels :channels :commands)))
                     http (http/start-server (gql/create-gql-handler schema resolvers @command-sender)
                                             {:port 8080})
                     st (c/closeable :state {:producer-channels @producer-channels
                                             :command-sender @command-sender
                                             :http http
                                             :notify-ch (fn [ev res]
                                                          (let [chan (-> @producer-channels :channels :notify)
                                                                v (assoc res :res-corr-id (:res-corr-id ev) :event-id (c/uuid))]
                                                            (chan v))
                                                          nil)
                                             :db @db})
                     system (kc/start-system @st handlers (-> @producer-channels :channels) bff/responder)]
           (do-with-state @st))
         (catch Throwable e
           (log/error "Error creating system" {} :cause e)))))

(def with-system
  (create-system [h/invite-customer
                  p/project-customer-to-simple-db]
                 [r/invite-response]))

(defonce state (atom nil))
(defonce instance (atom (future ::never-run)))

(defonce start (c/start-fn instance #(with-system (c/publishing-state c/forever state))))
(defonce stop (c/stop-fn instance))

;db writers
;write k,v (namespace key?) {:id :cust/423}
;write map in tx
;write tx (convert object to tx)  <- process
;write (honey?) sql () <- process

;db readers
;read by key
;read by key
;read by query
;read by query, where