(ns resolvers)

(defn first-of-type [type events]
  (some->> events
           (filter #(= (:type %) type))
           (first)))


(defn invite-response [{:keys [db events query]}]
  (when-let [id (or (:id (first-of-type :CustomerInvited events))
                    (:id (first-of-type :CustomerInviteFailed events)))]
    (db query {:id id})))
    
    