(ns handlers)

(def invite-customer
  ^{:in [:commands] :out :customer}
  (fn [{:keys [db notify-ch] :as ctx} {:keys [email type] :as event}]
    (case type
      :InviteCustomer
      (if (empty? email)
        {:type :CustomerInviteFailed
         :reason "Email is required"}
        (if (->> @db vals (map :email) (some #(= email %)))
          (notify-ch event {:type :CustomerInviteFailed
                            :reason "Customer already invited"})
          {:type :CustomerInvited
           :id (str (java.util.UUID/randomUUID))
           :email email}))
      nil)))