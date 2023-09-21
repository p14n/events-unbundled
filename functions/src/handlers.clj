(ns handlers
  (:require [common.core :as cc]))

(def invite-customer
  ^{:in [:commands] :out :customer :name :invite-customer-event-handler}
  (fn [{:keys [db notify-ch]} {:keys [email type] :as event}]
    (case type
      :InviteCustomer
      (if (empty? email)

        (notify-ch event {:type :error
                          :message "Email is required"})

        (if-let [id (->> @db vals (filter #(-> % :email (= email))) first :id)]

          (notify-ch event {:type :CustomerInviteFailed
                            :customer-id id
                            :reason "Customer already invited"})

          {:event-id (cc/uuid)
           :type :CustomerInvited
           :customer-id (cc/uuid)
           :email email}))
      nil)))