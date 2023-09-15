(ns async.core
  (:require [clojure.core.async :as a]
            [common.core :as cc])
  (:import [java.io Closeable]
           [java.lang Throwable]
           [java.lang Thread]))

;;Create a channel for handlers to listen to
;;Read/dispatch to handlers
;;Write to events channel


;;start projectors
;;start event handlers
;;start bff queue



(defn create-closable-channel []
  (cc/closeable (a/chan) a/close!))

(defn attach-handler ^Closeable [ctx ch handler]
  (let [running (atom true)]
    (a/go
      (while @running
        (let [event (a/<! ch)]
          (when event
            (println "Received event" event)
            (handler ctx event)))))
    (cc/closeable [ch running] (fn [[ch running]]
                                 (reset! running false)
                                 (println "Stopping handler" handler)
                                 (a/close! ch)))))

(defn attach-handlers [ctx in handlers]
  (let [multi-ch (a/mult in)]
    (->
     (doall (map (fn [handler]
                   (let [handler-ch (a/tap multi-ch (a/chan))]
                     (attach-handler ctx handler-ch handler))) handlers))
     (cc/closeable #(do (println "Closing attached handlers" %)
                        (doall (map (fn [c]
                                      (println "Closing handler" c)
                                      (.close c)) %)))))))

(defn wrap-handler [handler out]
  (fn [ctx event]
    (try
      (let [res (handler ctx event)]
        (when res
          (println "Sending result" res)
          (a/put! out res)))
      (catch Throwable e
        (println "Error in handler" e)))))

(defn start-system [ctx handlers command events]
  (let [system (attach-handlers ctx command (map #(wrap-handler % events) handlers))]
    (cc/closeable system (fn [system]
                           (println "Closing system")
                           (.close system)))))
