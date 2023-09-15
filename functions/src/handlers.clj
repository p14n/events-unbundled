(ns handlers)

(defn invite-customer [{:keys [db] :as ctx} {:keys [email type] :as event}]
  (case type
    :InviteCustomer
    (if (empty? email)
      {:type :CustomerInviteFailed
       :reason "Email is required"}
      {:type :CustomerInvited
       :id (str (java.util.UUID/randomUUID))
       :email email})
    nil))