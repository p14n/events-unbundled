(ns lambda-handlers
  (:require [handlers :as h]))

(defn invite-customer [x]
  (clj->js (h/invite-customer {} {:email (str x "@clojure.com")
                                  :type :InviteCustomer} {})))

(clj->js {:inviteCustomer invite-customer})

