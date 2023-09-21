(ns common.core
  (:require [com.kroo.epilogue :as log])
  (:import [java.util.concurrent CancellationException]
           [java.lang Thread]))

(defn closeable
  ([name value] (closeable name value identity))
  ([name value close] (reify
                        clojure.lang.IDeref
                        (deref [_] value)
                        java.io.Closeable
                        (close [_]
                          (log/info (str "Closing " name) {})
                          (close value)))))

(defn publishing-state [do-with-state target-atom]
  #(do (reset! target-atom %)
       (try (do-with-state %)
            (finally (reset! target-atom nil)))))

(defn forever [_]
  (try
    (.join (Thread/currentThread))
    (catch InterruptedException _ :stopped)))


(defn stop-fn [instance-atom]
  (fn []
    (let [instance-future @instance-atom]
      (future-cancel instance-future)
      (try @instance-future
           (catch CancellationException _ :stopped)))))

(defn start-fn [instance-atom init-fn]
  (fn []
    (swap! instance-atom
           #(if (realized? %)
              (future-call init-fn)
              (throw (ex-info "already running" {}))))))

(defn fn-name [f]
  (if-let [n (-> f meta :name)]
    n
    (str f)))

(defn first-of-type [type events]
  (some->> events
           (filter #(= (:type %) type))
           (first)))

(defn map-command-type-to-resolver [resolvers]
  (->> resolvers
       (map #(do [(-> % meta :type) %]))
       (into {})))

(defn get-all-channel-names [handlers]
  (->> handlers
       (map meta)
       (map (juxt :in :out))
       flatten
       (remove nil?)
       (set)))

(defn wrap-handler [handler ch-func ch-name]
  (let [fname (fn-name handler)
        _ (log/info "Attaching handler to outgoing channel" {:handler fname :channel ch-name})
        h (fn [ctx event]
            (try
              (let [id (:res-corr-id event)
                    res (handler ctx event)]
                (when res
                  (log/info "Sending result to channel" {:handler fname :channel ch-name :result res})
                  (ch-func (assoc res :res-corr-id id))))
              (catch Throwable e
                (log/error "Error in handler" {:handler fname :channel ch-name :event event} :cause e))))]
    (with-meta h (meta handler))))
