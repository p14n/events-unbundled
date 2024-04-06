(ns core-test
  (:require [clojure.test :refer :all]
            [simple-db.core :as sdb]
            [handlers :as h]
            [common.protocol :as prot]))

(deftest projects-invite-customer-test
  (testing "invite customer"
    (let [db (atom {})
          notify-events (atom [])
          ev (h/invite-customer {:db db} {:email "dean@p14n.com" :type :InviteCustomer} {})
          _ (prot/execute sdb/project-customer-to-simple-db
                          {:db db :notify-ch #(swap! notify-events conj %2)}
                          ev)

          result (sdb/invite-response {:db db} @notify-events)]
      (is (= {:email "dean@p14n.com" :invited true}
             (dissoc result :id))))))