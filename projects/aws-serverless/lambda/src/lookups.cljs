(ns lookups)

(defn invite-customer-lookup
  [{:keys [db]}
   {:keys [email type]}]
  (case type
    :InviteCustomer
    {:existing-id nil}))

(defn customer-lookup
  [{:keys [db]}
   {:keys [customer-id]}]
  {})