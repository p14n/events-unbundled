(ns projectors
  (:require [common.protocol :as prot]))

(def project-customer
  ^{:in [:customer] :name :customer-projector}
  (fn [_ event entity]
    (case (:type event)
      :CustomerInvited
      (-> entity
          (assoc :invited true)
          (assoc :email (:email event))
          (assoc :id (:customer-id event)))
      nil)))


(def project-customer-to-simple-db
  (prot/->Executor
   (prot/->LookupWriterHandler

    (fn [{:keys [db]} {:keys [customer-id]}]
      (->> customer-id
           (@db)))

    project-customer

    (fn [{:keys [db event-notify-ch]}
         {:keys [id] :as entity}]
      (swap! db assoc id entity)
      (event-notify-ch {:type :ProjectionComplete :customer-id id})))))


