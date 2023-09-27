(ns handlers
  (:require [common.core :as cc]
            [common.protocol :as prot]))

(def invite-customer
  ^{:in [:commands] :out :customer :name :invite-customer-event-handler}
  (fn [{:keys [notify-ch]}
       {:keys [email type]}
       {:keys [existing-id]}]
    (case type
      :InviteCustomer
      (if (empty? email)

        (notify-ch {:type :error
                    :message "Email is required"})

        (if existing-id
          (notify-ch {:type :CustomerInviteFailed
                      :customer-id existing-id
                      :reason "Customer already invited"})

          {:event-id (cc/uuid)
           :type :CustomerInvited
           :customer-id (cc/uuid)
           :email email}))
      nil)))

(def invite-customer-simple-db
  (prot/->Executor
   (prot/->LookupHandler
    (fn [{:keys [db]} {:keys [email type]}]
      (case type
        :InviteCustomer
        {:existing-id (some->> @db vals (filter #(-> % :email (= email))) first :id)}))
    invite-customer)))
  