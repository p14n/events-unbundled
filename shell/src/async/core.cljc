(ns async.core
  (:require [clojure.core.async :as a]
            [common.core :as cc]
            [com.kroo.epilogue :as log])
  (:import [java.io Closeable]
           [java.lang Throwable]))

;;Create a channel for handlers to listen to
;;Read/dispatch to handlers
;;Write to events channel


;;start projectors
;;start event handlers
;;start bff queue


(defn attach-handler ^Closeable [ctx ch handler ch-name]
  (log/info "Attaching handler to incoming channel" {:handler (str handler) :channel ch-name})
  (let [running (atom true)]
    (a/go
      (while @running
        (let [event (a/<! ch)]
          (when event
            (log/info "Received event" {:handler (str handler) :channel ch-name :event event})
            (handler ctx event)))))
    (cc/closeable [ch running] (fn [[ch running]]
                                 (reset! running false)
                                 (log/info "Stopping handler" {:handler (str handler)})
                                 (a/close! ch)))))

(defn attach-handlers [ctx in handlers ch-name]
  (let [multi-ch (a/mult in)]
    (->
     (doall (map (fn [handler]
                   (let [handler-ch (a/tap multi-ch (a/chan))]
                     (attach-handler ctx handler-ch handler ch-name))) handlers))
     (cc/closeable #(do
                      (log/info "Stopping handlers" {})
                      (doall (map (fn [c]
                                    (println "Closing handler" c)
                                    (.close c)) %)))))))

(defn wrap-handler [handler out ch-name]
  (log/info "Attaching handler to outgoing channel" {:handler (str handler) :channel ch-name})
  (let [h (fn [ctx event]
            (try
              (let [id (:res-corr-id event)
                    res (handler ctx event)]
                (when res
                  (log/info "Sending result to channel" {:handler (str handler) :channel ch-name :result res})
                  (a/put! out (assoc res :res-corr-id id))))
              (catch Throwable e
                (log/error "Error in handler" {:handler (str handler) :channel ch-name :event event} :cause e))))]
    (with-meta h (meta handler))))

(defn wrap-handlers [handlers channels]
  (->> handlers
       (map (fn [handler]
              (let [ch-name (some-> handler meta :out)]
                (if-let [out-ch (channels ch-name)]
                  (wrap-handler handler out-ch ch-name)
                  handler))))
       (remove nil?)))

(defn group-by-in [handlers]
  (->> handlers
       (reduce (fn [ac vl]
                 (->> vl meta :in
                      (reduce (fn [ac2 vl2]
                                (update ac2 vl2 #(conj % vl)))
                              ac)))
               {})))

(defn start-system [ctx handlers channels responder]
  (log/info "Starting system" {:handlers (map str handlers) :channels (keys channels)})
  (let [grouped-handlers (-> handlers
                             (wrap-handlers channels)
                             (group-by-in)
                             (update :notify conj responder))
        _ (println "Grouped handlers" grouped-handlers)
        system (doall (->> grouped-handlers
                           (map (fn [[ch-name ch-handlers]]
                                  (attach-handlers ctx (channels ch-name) (conj ch-handlers responder) ch-name)))))]
    (cc/closeable system (fn [system]
                           (log/info "Closing system" {})
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
