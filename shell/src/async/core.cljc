(ns async.core
  (:require [clojure.core.async :as a]
            [common.core :as cc]
            [com.kroo.epilogue :as log])
  (:import [java.io Closeable]
           [java.lang Throwable]))


(defn attach-handler ^Closeable [ctx ch handler ch-name]
  (let [running (atom true)
        fname (cc/fn-name handler)]
    (log/info "Attaching handler to incoming channel" {:handler fname :channel ch-name})
    (a/go
      (while @running
        (try
          (let [event (a/<! ch)]
            (when event
              (log/info (str "Received event:" fname) {:handler fname :channel ch-name :event event})
              (handler ctx event)))
          (catch Throwable e
            (log/error (str "Error in handler " fname) {:handler fname :channel ch-name} :cause e)))))
    (cc/closeable (keyword (str ch-name) fname)
                  [ch running] (fn [[ch running]]
                                 (reset! running false)
                                 (a/close! ch)))))

(defn attach-handlers [ctx in handlers ch-name]
  (let [multi-ch (a/mult in)]
    (->
     (doall (map (fn [handler]
                   (let [handler-ch (a/tap multi-ch (a/chan))]
                     (attach-handler ctx handler-ch handler ch-name))) handlers))
     (cc/closeable :handlers
                   #(do
                      (log/info "Stopping handlers" {})
                      (doall (map (fn [c] (.close c)) %)))))))

(defn wrap-handler [handler out ch-name]
  (let [fname (cc/fn-name handler)
        _ (log/info "Attaching handler to outgoing channel" {:handler fname :channel ch-name})
        h (fn [ctx event]
            (try
              (let [id (:res-corr-id event)
                    res (handler ctx event)]
                (when res
                  (log/info "Sending result to channel" {:handler fname :channel ch-name :result res})
                  (a/put! out (assoc res :res-corr-id id))))
              (catch Throwable e
                (log/error "Error in handler" {:handler fname :channel ch-name :event event} :cause e))))]
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
  (log/info "Starting system" {:handlers (map cc/fn-name handlers) :channels (keys channels)})
  (let [grouped-handlers (-> handlers
                             (wrap-handlers channels)
                             (group-by-in)
                             (update :notify conj responder))
        system (doall (->> grouped-handlers
                           (map (fn [[ch-name ch-handlers]]
                                  (attach-handlers ctx (channels ch-name) (conj ch-handlers responder) ch-name)))))]
    (cc/closeable :system system (fn [system]
                                   (log/info "Closing system" {})
                                   (->> system (map #(.close %)))))))

(defn create-all-channels-closable [channel-names]
  (let [channels (->> channel-names
                      (map #(do [% (a/chan)]))
                      (into {}))]
    (cc/closeable :channels channels #(doall (->> % vals (map a/close!))))))
