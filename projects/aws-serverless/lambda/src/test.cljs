(ns test
  (:require [promesa.core :as p]
            [dynamodb-tools :as ddb]))


;; (p/let [y nil
;;         client (ddb/create-client)
;;         _ (println "a")
;;         reqs [(ddb/create-table-request "events" [(ddb/create-event-record
;;                                                    {:type "Boo" :email "hello"
;;                                                     :event-id "1" :correlation-id "2"} "customer")])]
;;         _ (println "b")
;;         x (ddb/write-all-table-requests client reqs)]
;;   (println "xxxx " x))

(println (.toISOString (js/Date.)))