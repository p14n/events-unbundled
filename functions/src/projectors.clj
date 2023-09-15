(ns projectors)

(defn project-customer [{:keys [entity] :as ctx} event]
  (case (:type event)
    :CustomerInvited
    (-> entity
        (assoc :invited true)
        (assoc :email (:email event))
        (assoc :id (:id event)))))
  