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

(defn start-system [ctx handlers producer-channels responder]
  (log/info "Starting system" {:handlers (map cc/fn-name handlers) :channels (-> producer-channels :channels keys)})
  (let [wrapped-handlers (wrap-handlers handlers (-> producer-channels :channels))
        system (doall (->> wrapped-handlers
                           (map (fn [handler]
                                  (when-let [ch-names (some-> handler meta :in)]
                                    (kc/attach-handler ctx handler ch-names))))
                           (remove nil?)
                           (conj (kc/attach-handler ctx responder (-> producer-channels :channels keys)))))]
    (cc/closeable :system system (fn [system]
                                   (log/info "Closing system" {})
                                   (doall (->> system (map #(.close %))))))))
