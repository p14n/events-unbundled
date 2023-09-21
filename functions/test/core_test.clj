(ns core-test
  (:require [clojure.test :refer :all]
            [handlers :as h]
            [resolvers :as r]
            [projectors :as p]))

(deftest projects-invite-customer-test
  (testing "invite customer"
    (let [db (atom {})
          notify-events (atom [])

          _ (->> {:email "dean@p14n.com" :type :InviteCustomer}
                 (h/invite-customer {:db db})
                 (p/project-customer-to-simple-db
                  {:db db
                   :notify-ch #(swap! notify-events conj %2)}))

          result (r/invite-response {:db db} @notify-events)]
      (is (= {:email "dean@p14n.com" :invited true}
             (dissoc result :id))))))