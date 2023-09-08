(ns resolvers)

(defn first-of-type [type events]
  (some->> events
           (filter #(= (:type %) type))
           (first)))

(defn query-domain [query args]
  {:id (:id args)
   :email "x@k.com"
   :invited true})

(defn invite-response [{:keys [events query]}]
  (when-let [id (or (:id (first-of-type :CustomerInvited events))
                    (:id (first-of-type :CustomerInviteFailed events)))]
    (query-domain query {:id id})))
    
    