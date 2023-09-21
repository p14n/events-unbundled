(ns kafka.core
  (:require [common.core :as cc]
            [kafka.consumer :as kc]
            [com.kroo.epilogue :as log]))

(defn wrap-handlers [handlers channels]
  (->> handlers
       (map (fn [handler]
              (let [ch-name (some-> handler meta :out)]
                (if-let [out-ch (channels ch-name)]
                  (cc/wrap-handler handler out-ch ch-name)
                  handler))))
       (remove nil?)))

(defn start-system [ctx handlers channels responder]
  (log/info "Starting system" {:handlers (map cc/fn-name handlers) :channels (keys channels)})
  (let [wrapped-handlers (wrap-handlers handlers channels)
        system (doall (->> wrapped-handlers
                           (map (fn [handler]
                                  (when-let [ch-names (some-> handler meta :in)]
                                    (kc/attach-handler ctx handler ch-names))))
                           (remove nil?)
                           (concat [(kc/attach-handler ctx responder (keys channels))])))]
    (cc/closeable :system system (fn [system]
                                   (log/info "Closing system" {})
                                   (doall (->> system (map #(.close %))))))))
