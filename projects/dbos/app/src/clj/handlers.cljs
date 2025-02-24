(ns clj.handlers
  (:require [handlers :as h]
            [promesa.core :as p]
            [clj.shell :refer [make-handler make-lookup make-write rows->clj]]))


(def handlers
  {:inviteCustomer {:handler (make-handler (with-meta h/invite-customer
                                             {:receives #{:InviteCustomer}
                                              :returns #{:CustomerInvited :CustomerInviteFailed}}))
                    :lookup (make-lookup (fn [{:keys [db]} {:keys [email]}]
                                           (p/let [r (db "SELECT cid FROM customers WHERE email = ?" email)]
                                             {:existing-id (some-> r (rows->clj) (first) :cid)})))
                    :write (make-write (fn [{:keys [db]} {:keys [type email customer-id]}]
                                         (when (and email customer-id (= type :CustomerInvited))
                                           (db "INSERT INTO customers (cid,email) VALUES (?,?)" customer-id email))))}
   :verifyCustomer {:handler (make-handler (with-meta (fn [c e l]
                                                        {:type :CustomerVerified})
                                             {:receives #{:CustomerInvited}
                                              :returns #{:CustomerVerified
                                                         :CustomerVerifyFailed}}))}
   :communicateToCustomer {:handler (make-handler (with-meta (fn [c {:keys [type]} l]
                                                               (println "Communicating to customer" type))
                                                    {:receives #{:CustomerInvited
                                                                 :CustomerVerified
                                                                 :CustomerVerifyFailed}}))}})

(clj->js handlers)


