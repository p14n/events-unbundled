(ns lookups
  (:require [promesa.core :as p]
            [dynamodb-tools :as ddb]))

(defn prx [x] (js/console.log x) x)

(defn invite-customer-lookup
  [{:keys [db]}
   {:keys [email type]}]
  (case type
    :InviteCustomer
    (p/let [result (.send db (ddb/create-get-item-command "customer_emails" {"email" {"S" email}}))
            to-return {:existing-id (some-> result prx ddb/result-item :customer-id :S)}]
      (prx to-return))))

(defn customer-lookup
  [{:keys [db]}
   {:keys [customer-id]}]
  (p/let [result (.send db (ddb/create-get-item-command "customers" {"id" {"S" customer-id}}))
          to-return (some-> result prx ddb/result->object)]
    (prx to-return)))