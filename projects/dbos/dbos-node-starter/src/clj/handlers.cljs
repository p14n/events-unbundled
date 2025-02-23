(ns clj.handlers
  (:require [handlers :as h]
            [promesa.core :as p]
            [clj.shell :refer [make-handler make-lookup rows->clj]]))

(clj->js {:inviteCustomer {:handler (make-handler h/invite-customer)
                           :lookup (make-lookup (fn [{:keys [db]} {:keys [email]}]
                                                  (p/let [r (db "SELECT cid FROM customers WHERE email = ?" email)]
                                                    {:existing-id (some-> r (rows->clj) (first) :cid)})))}})
