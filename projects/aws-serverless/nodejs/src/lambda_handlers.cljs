(ns lambda-handlers
  (:require [promesa.core :as pc]
            [dynamodb-tools :as ddb]
            [handlers :as h]
            [projectors :as p]
            [shell :as s]))

(defn invite-customer-lookup
  [{:keys [db]}
   {:keys [email type]}]
  (case type
    :InviteCustomer
    (pc/let [result (ddb/single-item-fetch db "customer_emails" {"email" {"S" email}})]
      {:existing-id (some-> result :customer-id)})))

(defn customer-lookup
  [{:keys [db]}
   {:keys [customer-id]}]
  (pc/let [result (ddb/single-item-fetch db "customers" {"id" {"S" customer-id}})]
    result))

(defn create-customer-email [_ {:keys [email customer-id]}]
  (ddb/create-table-put-requests "customer_emails" [{"email" {"S" email}
                                                     "customer-id" {"S" customer-id}}]))

(defn update-customer [_ {:keys [email id invited]}]
  (with-meta (ddb/create-table-put-requests "customers" [{"id" {"S" id}
                                                          "email" {"S" email}
                                                          "invited" {"BOOL" invited}}])
    {:type :ProjectionComplete :customer-id id}))

(clj->js {(s/handler-name-kw h/invite-customer) (s/create-lookup-writer-handler h/invite-customer invite-customer-lookup create-customer-email)
          (s/handler-name-kw p/project-customer) (s/create-lookup-writer-handler p/project-customer customer-lookup update-customer)})


