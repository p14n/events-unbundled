(ns lambda-handlers
  (:require [promesa.core :as pc]
            [dynamodb-tools :as ddb]
            [handlers :as h]
            [projectors :as p]
            [shell :as s]))

(defn prx [x] (js/console.log x) x)

(defn invite-customer-lookup
  [{:keys [db]}
   {:keys [email type]}]
  (case type
    :InviteCustomer
    (pc/let [result (.send db (ddb/create-get-item-command "customer_emails" {"email" {"S" email}}))
             to-return {:existing-id (some-> result prx ddb/result-item :customer-id :S)}]
      (prx to-return))))

(defn customer-lookup
  [{:keys [db]}
   {:keys [customer-id]}]
  (pc/let [result (.send db (ddb/create-get-item-command "customers" {"id" {"S" customer-id}}))
           to-return (some-> result prx ddb/result->object)]
    (prx to-return)))

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


