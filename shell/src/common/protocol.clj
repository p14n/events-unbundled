(ns common.protocol)

(defprotocol IExecute
  (execute [this ctx event])
  (executor-meta [this]))

(defprotocol IHandler
  (lookup [this ctx event])
  (transform [this ctx event data])
  (write [this ctx event])
  (transformer-meta [this]))

(deftype Executor [^common.protocol.IHandler h]
  IExecute
  (execute [_ ctx event]
    (let [_ctx (assoc ctx :notify-ch (partial (:notify-ch ctx) event))]
      (->> event
           (lookup h _ctx)
           (transform h _ctx event)
           (write h _ctx))))
  (executor-meta [_]
    (transformer-meta h)))

(deftype SimpleHandler [transformer]
  IHandler
  (lookup [_ _ _] {})
  (transform [_ ctx event data]
    (transformer ctx event data))
  (write [_ _ event] event)
  (transformer-meta [_] (clojure.core/meta transformer)))

(deftype LookupHandler [looker-upper transformer]
  IHandler
  (lookup [_ ctx event] (looker-upper ctx event))
  (transform [_ ctx event data]
    (transformer ctx event data))
  (write [_ _ event] event)
  (transformer-meta [_] (clojure.core/meta transformer)))

(deftype LookupWriterHandler [looker-upper transformer writer]
  IHandler
  (lookup [_ ctx event] (looker-upper ctx event))
  (transform [_ ctx event data]
    (transformer ctx event data))
  (write [_ ctx event] (writer ctx event))
  (transformer-meta [_] (clojure.core/meta transformer)))

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