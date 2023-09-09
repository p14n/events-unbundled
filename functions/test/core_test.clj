(ns core-test
  (:require [clojure.test :refer :all]
            [handlers :as h]
            [resolvers :as r]
            [projectors :as p]))

(deftest projects-invite-customer-test
  (testing "invite customer"
    (let [cmd {:email "dean@p14n.com"}
          ev (h/invite-customer {:event cmd})
          prj (p/project-customer {:event ev})
          db (fn [q {:keys [id]}] (when (= id (:id ev)) prj))
          result (r/invite-response {:events [ev] :db db})]
      (is (= {:email "dean@p14n.com" :invited true}
             (dissoc result :id))
          (is (= (:id ev) (:id result)))))))