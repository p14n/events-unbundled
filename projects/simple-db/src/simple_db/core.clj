(ns simple-db.core
  (:require [handlers :as h]
            [projectors :as p]
            [resolvers :as r]
            [common.protocol :as prot]))

(def invite-customer-simple-db
  (prot/->Executor
   (prot/->LookupHandler
    (fn [{:keys [db]} {:keys [email type]}]
      (case type
        :InviteCustomer
        {:existing-id (some->> @db vals (filter #(-> % :email (= email))) first :id)}))
    h/invite-customer)))


(def project-customer-to-simple-db
  (prot/->Executor
   (prot/->LookupWriterHandler

    (fn [{:keys [db]} {:keys [customer-id]}]
      (->> customer-id
           (@db)))

    p/project-customer

    (fn [{:keys [db event-notify-ch]}
         {:keys [id] :as entity}]
      (swap! db assoc id entity)
      (println db id entity)
      (event-notify-ch {:type :ProjectionComplete :customer-id id})))))


(def invite-response
  (r/invite-responser #(get (deref %1) %2)))
    