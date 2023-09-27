(ns kafka.consumer
  (:gen-class)
  (:require
   [clojure.core.async :as a]
   [kafka.common :as kc]
   [common.core :as cc]
   [com.kroo.epilogue :as log]
   [common.protocol :as prot])
  (:import
   (java.time Duration)
   (java.io Closeable)
   (org.apache.kafka.clients.consumer ConsumerConfig KafkaConsumer)))

(defn consumer-props [id]
  {ConsumerConfig/GROUP_ID_CONFIG                 id
   ConsumerConfig/KEY_DESERIALIZER_CLASS_CONFIG   "org.apache.kafka.common.serialization.StringDeserializer"
   ConsumerConfig/VALUE_DESERIALIZER_CLASS_CONFIG "org.apache.kafka.common.serialization.StringDeserializer"})


(defn attach-handler ^Closeable [ctx handler ch-names]
  (let [running (atom true)
        fname (cc/fn-name handler)
        consumer (KafkaConsumer. (kc/build-properties (consumer-props (str "cqrs_" fname))))]
    (.subscribe consumer (map name ch-names))
    (log/info "Attaching handler to incoming channel" {:handler fname :channels ch-names})
    (a/go
      (loop [records []]
        (println "KafkaConsumer.poll" (count records))
        (doall (map (fn [record]
                      (println "Received record" record)
                      (try
                        (let [_ (println "x")
                              event (some-> record (.value) (read-string))]
                          (when event
                            (log/info (str "Received event:" fname) {}
                                      ;{:handler fname :channel (.topic record) :event event}
                                      )
                            (prot/execute handler ctx event)))
                        (catch Throwable e
                          (.printStackTrace e)
                          ;(log/error (str "Error in handler " fname) {:handler fname :channels ch-names} :cause e)
                          )))
                    records))
        (if @running
          (recur (seq (.poll consumer (Duration/ofSeconds 1))))
          (.close consumer))))
    (cc/closeable (keyword fname)
                  running (fn [running]
                            (reset! running false)))))

