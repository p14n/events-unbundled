(ns clj.handlers
  (:require [handlers :as h]
            [promesa.core :as p]
            [clj.shell :refer [make-handler make-lookup make-write rows->clj]]))


(def handlers
  {:inviteCustomer {:handler (make-handler h/invite-customer)
                    :lookup (make-lookup (fn [{:keys [db]} {:keys [email]}]
                                           (p/let [r (db "SELECT cid FROM customers WHERE email = ?" email)]
                                             {:existing-id (some-> r (rows->clj) (first) :cid)})))
                    :write (make-write (fn [{:keys [db]} {:keys [email customer-id]}]
                                         (db "INSERT INTO customers (cid,email) VALUES (?,?)" customer-id email)))}})

(clj->js handlers)
