(ns projectors)

(defn project-customer [event entity]
  (case (:type event)
    :CustomerInvited
    (-> entity
        (assoc :invited true)
        (assoc :email (:email event))
        (assoc :id (:id event)))
    nil))

(def project-customer-to-simple-db
  ^{:in [:customer]}
  (fn [{:keys [db notify-ch]} {:keys [id] :as event}]
    (->> id
         (@db)
         (project-customer event)
         (swap! db assoc id))
    (notify-ch event {:type :ProjectionComplete
                      :id id})))
    
