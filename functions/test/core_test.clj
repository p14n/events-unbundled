(ns core-test
  (:require [clojure.test :refer :all]
            [handlers :as h]
            [projectors :as p]))

(deftest projects-invite-customer-test
  (testing "invite customer"
    (let [cmd {:email "dean@p14n.com" :type :InviteCustomer}
          ev (h/invite-customer {} cmd {})
          en (p/project-customer nil ev {})]
      (is (= {:email "dean@p14n.com" :invited true}
             (dissoc en :id))))))