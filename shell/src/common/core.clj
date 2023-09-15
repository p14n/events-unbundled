(ns common.core
  (:import [java.util.concurrent CancellationException]
           [java.lang Thread]))

(defn closeable
  ([value] (closeable value identity))
  ([value close] (reify
                   clojure.lang.IDeref
                   (deref [_] value)
                   java.io.Closeable
                   (close [_] (close value)))))

(defn publishing-state [do-with-state target-atom]
  #(do (reset! target-atom %)
       (try (do-with-state %)
            (finally (reset! target-atom nil)))))

(defn forever [_]
  (.join (Thread/currentThread)))


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
