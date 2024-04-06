(ns lookups)

(defn invite-customer-lookup
  [{:keys [db]}
   {:keys [email type]}]
  (case type
    :InviteCustomer
    {:existing-id nil}))