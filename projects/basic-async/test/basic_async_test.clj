(ns basic-async-test
  (:require [clojure.test :refer :all]
            [core :as sut]))

(deftest test-invite
  (sut/with-system
    (fn [state]
      ((-> state :command-sender) {:type :InviteCustomer :email "dean@p14n.com"})
      (is (= true
             (-> state :db deref vals first :invited))))))
