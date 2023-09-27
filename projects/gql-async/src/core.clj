(ns core
  (:require [async.core :as ac]
            [clojure.core.async :as a]
            [handlers :as h]
            [projectors :as p]
            [resolvers :as r]
            [common.core :as c]
            [bff.cache :as bff]
            [aleph.http :as http]
            [bff.graphql :as gql]
            [com.kroo.epilogue :as log]))


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
    (try (with-open [channels (->> (conj (c/get-all-channel-names handlers) :commands :notify)
                                   ac/create-all-channels-closable)
                     db (c/closeable :db (atom {}) #(reset! % {}))
                     command-sender (c/closeable :command-sender (bff/create-command-sender
                                                                  (c/map-command-type-to-resolver resolvers)
                                                                  (fn [cmd] (a/put! (:commands @channels) cmd
                                                                                    (fn [a] (println "Command sent " a cmd))))))
                     http (http/start-server (gql/create-gql-handler schema resolvers @command-sender)
                                             {:port 8080})
                     st (c/closeable :state {:channels @channels
                                             :command-sender @command-sender
                                             :http http
                                             :notify-ch (fn [ev res]
                                                          (a/put! (:notify @channels) (assoc res :res-corr-id (:res-corr-id ev)))
                                                          nil)
                                             :db @db})
                     system (ac/start-system @st handlers @channels bff/responder-executor)]
           (do-with-state @st))
         (catch Throwable e
           (log/error "Error creating system" {} :cause e)))))

(def with-system
  (create-system [h/invite-customer-simple-db
                  p/project-customer-to-simple-db]
                 [r/invite-response]))

(defonce state (atom nil))
(defonce instance (atom (future ::never-run)))

(defonce start (c/start-fn instance #(with-system (c/publishing-state c/forever state))))
(defonce stop (c/stop-fn instance))

