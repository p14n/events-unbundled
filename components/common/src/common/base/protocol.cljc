(ns common.base.protocol)

(defprotocol IExecute
  (execute [this ctx event])
  (executor-meta [this]))

(defprotocol IHandler
  (lookup [this ctx event])
  (operate [this ctx event data])
  (write [this ctx event])
  (operator-meta [this]))

