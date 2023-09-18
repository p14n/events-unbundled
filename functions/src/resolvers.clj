(ns resolvers
  (:require [common.core :refer [first-of-type]]))


(def invite-response
  ^{:type :InviteCustomer}
  (fn [{:keys [db]} events]
    (when-let [id (or (:id (first-of-type :CustomerInvited events))
                      (:id (first-of-type :CustomerInviteFailed events)))]
      (get @db id))))
    
    