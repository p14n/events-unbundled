(ns core-test
  (:require [core :as c]
            [test-util :as tu]))

(defn start-test-system []
  (future-call #(c/with-system (fn [_] (tu/system-holder 10))))
  (tu/system-waiter))

(defn stop-test-system []
  (tu/system-closer))