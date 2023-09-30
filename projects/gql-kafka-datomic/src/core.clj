(ns core
  (:require [handlers :as h]
            [resolvers :as r]
            [common.core :as c]
            [bff.cache :as bff]
            [aleph.http :as http]
            [bff.graphql :as gql]
            [kafka.producer :as kp]
            [com.kroo.epilogue :as log]
            [kafka.core :as kc]
            [common.protocol :as prot]
            [datomic.core :as d]
            [datomic.api :as da]))


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

(def db-schema [#:db{:ident :customer/id,
                     :valueType :db.type/string,
                     :cardinality :db.cardinality/one,
                     :db/unique :db.unique/identity,
                     :doc "Customer Identifier"}
                #:db{:ident :customer/email,
                     :valueType :db.type/string,
                     :cardinality :db.cardinality/one,
                     :db/unique :db.unique/identity,
                     :doc "Customer Email"}
                #:db{:ident :customer/invited,
                     :valueType :db.type/boolean,
                     :cardinality :db.cardinality/one,
                     :doc "Has the customer been invited?"}])

(defn do-pull [db where]
  (da/pull (da/db db) '[*] where))

(def invite-customer-datomic
  (prot/->Executor
   (prot/->LookupWriterHandler
    (fn [{:keys [db]} {:keys [email type]}]
      (case type
        :InviteCustomer
        (let [ex (some-> (do-pull db [:customer/email email])
                         (get :customer/id))]
          (when ex
            {:existing-id ex}))))

    h/invite-customer

    (fn [{:keys [db event-notify-ch]} {:keys [email type customer-id] :as event}]
      (case type
        :CustomerInvited
        (do (-> (da/transact db [{:customer/email email
                                  :customer/id customer-id
                                  :customer/invited true}]))

            (event-notify-ch {:type :ProjectionComplete :customer-id customer-id})))
      event))))


(defn create-system [handlers resolvers]
  (fn [do-with-state]
    (try (with-open [producer-channels (-> handlers
                                           (c/get-all-channel-names)
                                           (conj :commands :notify)
                                           (kp/create-producer-channels))
                     db (d/create-db db-schema)
                     command-sender (c/closeable :command-sender (bff/create-command-sender
                                                                  (c/map-command-type-to-resolver resolvers)
                                                                  (-> @producer-channels :channels :commands)))
                     http (http/start-server (gql/create-gql-handler schema resolvers @command-sender)
                                             {:port 8080})
                     st (c/closeable :state {:producer-channels @producer-channels
                                             :command-sender @command-sender
                                             :db @db
                                             :http http
                                             :notify-ch (fn [ev res]
                                                          (let [chan (-> @producer-channels :channels :notify)
                                                                v (assoc res :res-corr-id (:res-corr-id ev) :event-id (c/uuid))]
                                                            (chan v))
                                                          nil)})
                     system (kc/start-system @st handlers (-> @producer-channels :channels) bff/responder-executor)]
           (do-with-state @st))
         (catch Throwable e
           (log/error "Error creating system" {} :cause e)))))

(def with-system
  (create-system [invite-customer-datomic]
                 [(r/invite-responser #(->> (da/pull (da/db %1) '[*]  [:customer/id %2])
                                            (map (fn [[k v]] [(-> k name keyword) v]))
                                            (into {})))]))

(defonce state (atom nil))
(defonce instance (atom (future ::never-run)))

(defonce start (c/start-fn instance #(with-system (c/publishing-state c/forever state))))
(defonce stop (c/stop-fn instance))

