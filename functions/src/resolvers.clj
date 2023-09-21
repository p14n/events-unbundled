(ns resolvers
  (:require [common.core :refer [first-of-type]]))


(def invite-response
  ^{:type :InviteCustomer}
  (fn [{:keys [db]} events]
    (println "invite-response" events)
    (if-let [id (or (:customer-id (first-of-type :ProjectionComplete events))
                    (:customer-id (first-of-type :CustomerInviteFailed events)))]
      (get @db id)
      (println "No id found"))))
    
    