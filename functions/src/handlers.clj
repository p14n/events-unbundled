(ns handlers)

(defn invite-customer [{:keys [db] {:keys [email]} :event :as ctx}]
  (if (empty? email)
    {:type :CustomerInviteFailed
     :reason "Email is required"}
    {:type :CustomerInvited
     :id (str (java.util.UUID/randomUUID))
     :email email}))