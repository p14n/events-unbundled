(ns projectors)

(def project-customer
  ^{:in [:customer] :name :customer-projector}
  (fn [_ event entity]
    (case (:type event)
      :CustomerInvited
      (-> entity
          (assoc :invited true)
          (assoc :email (:email event))
          (assoc :id (:customer-id event)))
      nil)))
