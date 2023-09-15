(ns async.core
  (:require [clojure.core.async :as a]
            [common.core :as cc])
  (:import [java.io Closeable]
           [java.lang Throwable]))

;;Create a channel for handlers to listen to
;;Read/dispatch to handlers
;;Write to events channel


;;start projectors
;;start event handlers
;;start bff queue


(defn attach-handler ^Closeable [ctx ch handler]
  (println "Attaching handler" handler "to incoming channel" ch)
  (let [running (atom true)]
    (a/go
      (while @running
        (let [event (a/<! ch)]
          (when event
            (println "Received event" event handler)
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
  (println "Wrapping handler" handler "to outgoing channel" out)
  (let [h (fn [ctx event]
            (try
              (let [res (handler ctx event)]
                (when res
                  (println "Sending result" res)
                  (a/put! out res)))
              (catch Throwable e
                (println "Error in handler" e))))]
    (with-meta h (meta handler))))

(defn wrap-handlers [channels handlers]
  (map (fn [handler]
         (if-let [out-ch (-> handler meta :out channels)]
           (wrap-handler handler out-ch)
           handler)) handlers))

(defn group-by-in [handlers]
  (->> handlers
       (reduce (fn [ac vl]
                 (->> vl meta :in
                      (reduce (fn [ac2 vl2]
                                (update ac2 vl2 #(conj % vl)))
                              ac)))
               {})))

(defn start-system [ctx handlers channels]
  (println "Starting system" handlers channels)
  (let [grouped-handlers (->> handlers
                              (wrap-handlers channels)
                              (group-by-in))
        system (doall (->> grouped-handlers
                           (map (fn [[ch-name ch-handlers]]
                                  (attach-handlers ctx (channels ch-name) ch-handlers)))))]
    (cc/closeable system (fn [system]
                           (println "Closing system")
                           (->> system (map #(.close %)))))))

(defn get-all-channel-names [handlers]
  (->> handlers
       (map meta)
       (map (juxt :in :out))
       flatten
       (remove nil?)
       (set)))

(defn create-all-channels-closable [channel-names]
  (let [channels (->> channel-names
                      (map #(do [% (a/chan)]))
                      (into {}))]
    (cc/closeable channels #(doall (->> % vals (map a/close!))))))
