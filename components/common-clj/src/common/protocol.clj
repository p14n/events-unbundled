(ns common.protocol
  (:require [common.base.protocol :as p]))

(def IExecute p/IExecute)
(def IHandler p/IHandler)
(def write p/write)
(def operate p/operate)
(def lookup p/lookup)
(def operator-meta p/operator-meta)
(def executor-meta p/executor-meta)
(def execute p/execute)

(deftype Executor [h]
  IExecute
  (execute [_ ctx event]
    (let [_ctx (assoc ctx :event-notify-ch (partial (:notify-ch ctx) event))]
      (->> event
           (lookup h _ctx)
           (operate h _ctx event)
           (write h _ctx))))
  (executor-meta [_]
    (operator-meta h)))

(deftype SimpleHandler [operator]
  IHandler
  (lookup [_ _ _] {})
  (operate [_ ctx event data]
    (operator ctx event data))
  (write [_ _ event] event)
  (operator-meta [_] (clojure.core/meta operator)))

(deftype LookupHandler [looker-upper operator]
  IHandler
  (lookup [_ ctx event] (looker-upper ctx event))
  (operate [_ ctx event data]
    (operator ctx event data))
  (write [_ _ event] event)
  (operator-meta [_] (clojure.core/meta operator)))

(deftype LookupWriterHandler [looker-upper operator writer]
  IHandler
  (lookup [_ ctx event] (looker-upper ctx event))
  (operate [_ ctx event data]
    (operator ctx event data))
  (write [_ ctx event] (writer ctx event))
  (operator-meta [_] (clojure.core/meta operator)))

;db writers
;write k,v (namespace key?) {:id :cust/423}
;write map in tx
;write tx (convert object to tx)  <- process
;write (honey?) sql () <- process

;db readers
;read by key
;read by key
;read by query
;read by query, where


;For map/xtdb
;lookup by id or pair
;simple-handler
;handler-with-lookup