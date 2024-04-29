(ns writers
  (:require [dynamodb-tools :as ddb]))

(defn create-customer-email [_ {:keys [email customer-id]}]
  (ddb/create-table-put-requests "customer_emails" [{"email" {"S" email}
                                                     "customer-id" {"S" customer-id}}]))

(defn update-customer [_ {:keys [email id invited]}]
  (ddb/create-table-put-requests "customers" [{"id" {"S" id}
                                               "email" {"S" email}
                                               "invited" {"BOOL" invited}}]))