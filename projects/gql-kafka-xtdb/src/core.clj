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
            [xtdb.core :as xtc]
            [xtdb.api :as xt]
            [common.protocol :as prot]))


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

;
;(xt/entity (xt/db @db) :some-id)

(def invite-customer-xtdb
  (prot/->Executor
   (prot/->LookupHandler
    (fn [{:keys [db]} {:keys [email type]}]
      (case type
        :InviteCustomer
        (let [ex (ffirst (xt/q (xt/db db) {:find '[e] :where [['e :email email]]}))]

          (when ex
            {:existing-id ex}))))
    h/invite-customer)))

(def project-customer-xtdb
  (prot/->Executor
   (prot/->LookupWriterHandler

    (fn [{:keys [db]} {:keys [customer-id]}]
      (xt/entity (xt/db db) customer-id))

    p/project-customer

    (fn [{:keys [db event-notify-ch]}
         {:keys [id] :as entity}]
      (xt/submit-tx db [[::xt/put (assoc entity :xt/id id)]])
      (xt/sync db)
      (event-notify-ch {:type :ProjectionComplete :customer-id id})))))

(defn create-system [handlers resolvers]
  (fn [do-with-state]
    (try (with-open [producer-channels (-> handlers
                                           (c/get-all-channel-names)
                                           (conj :commands :notify)
                                           (kp/create-producer-channels))
                     db (xtc/start-node "xtdb")
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
                     system (kc/start-system @st handlers (-> @producer-channels :channels) bff/responder-executor)]
           (do-with-state @st))
         (catch Throwable e
           (log/error "Error creating system" {} :cause e)))))

(def with-system
  (create-system [invite-customer-xtdb
                  project-customer-xtdb]
                 [(r/invite-responder #(xt/entity (xt/db %1) %2))]))

(defonce state (atom nil))
(defonce instance (atom (future ::never-run)))

(defonce start (c/start-fn instance #(with-system (c/publishing-state c/forever state))))
(defonce stop (c/stop-fn instance))

