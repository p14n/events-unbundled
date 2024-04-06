(ns handlers
  (:require [common.core :as cc]))

(def invite-customer
  ^{:in [:commands] :out :customer :name :invite-customer-event-handler}
  (fn [{:keys [event-notify-ch]}
       {:keys [email type]}
       {:keys [existing-id]}]
    (case type
      :InviteCustomer
      (if (empty? email)

        (event-notify-ch {:type :error
                          :message "Email is required"})

        (if existing-id
          (event-notify-ch {:type :CustomerInviteFailed
                            :customer-id existing-id
                            :reason "Customer already invited"})

          {:event-id (cc/uuid)
           :type :CustomerInvited
           :customer-id (cc/uuid)
           :email email}))
      nil)))