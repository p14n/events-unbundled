(ns handlers)

(def invite-customer
  ^{:in [:commands] :out :customer}
  (fn [{:keys [db notify-ch]} {:keys [email type] :as event}]
    (case type
      :InviteCustomer
      (if (empty? email)

        {:type :CustomerInviteFailed
         :reason "Email is required"}

        (if-let [id (->> @db vals (filter #(-> % :email (= email))) first :id)]

          (notify-ch event {:type :CustomerInviteFailed
                            :customer-id id
                            :reason "Customer already invited"})

          {:event-id (str (java.util.UUID/randomUUID))
           :type :CustomerInvited
           :customer-id (str (java.util.UUID/randomUUID))
           :email email}))
      nil)))