(ns core-test
  (:require [clojure.test :refer :all]
            [handlers :as h]
            [resolvers :as r]
            [projectors :as p]))

(deftest projects-invite-customer-test
  (testing "invite customer"
    (let [cmd {:email "dean@p14n.com" :type :InviteCustomer}
          ev (h/invite-customer {:db (atom {})} cmd)
          prj (p/project-customer ev {})
          result (r/invite-response {:db (atom {(:id ev) prj})} [ev])]
      (is (= {:email "dean@p14n.com" :invited true}
             (dissoc result :id))
          (is (= (:id ev) (:id result)))))))