(ns resolvers
  (:require [common.core :refer [first-of-type]]))


(defn invite-responser [db-lookup]
  ^{:type :InviteCustomer}
  (fn [{:keys [db]} events]
    (println "invite-response" events)
    (if-let [id (or (:customer-id (first-of-type :ProjectionComplete events))
                    (:customer-id (first-of-type :CustomerInviteFailed events)))]
      (db-lookup db id)
      (println "No id found"))))

(def invite-response
  (invite-responser #(get %1 %2)))
    
    