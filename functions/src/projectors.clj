(ns projectors)

(defn project-customer [event entity]
  (case (:type event)
    :CustomerInvited
    (-> entity
        (assoc :invited true)
        (assoc :email (:email event))
        (assoc :id (:customer-id event)))
    nil))

(def project-customer-to-simple-db
  ^{:in [:customer]}
  (fn [{:keys [db notify-ch]} {:keys [customer-id] :as event}]
    (->> customer-id
         (@db)
         (project-customer event)
         (swap! db assoc customer-id))
    (notify-ch event {:type :ProjectionComplete
                      :customer-id customer-id})))
    
