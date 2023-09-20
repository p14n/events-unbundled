(ns kafka.producer
  (:require [kafka.common :as kc]
            [com.kroo.epilogue :as log]
            [common.core :as cc])
  (:import
   (org.apache.kafka.clients.admin AdminClient NewTopic)
   (org.apache.kafka.clients.producer Callback KafkaProducer ProducerConfig ProducerRecord)
   (org.apache.kafka.common.errors TopicExistsException)))

(def producer-props {ProducerConfig/KEY_SERIALIZER_CLASS_CONFIG   "org.apache.kafka.common.serialization.StringSerializer"
                     ProducerConfig/VALUE_SERIALIZER_CLASS_CONFIG "org.apache.kafka.common.serialization.StringSerializer"})

(defn create-topic!
  ([topic props]
   (create-topic! topic 1 3 props))
  ([topic partitions replication cloud-config]
   (let [ac (AdminClient/create cloud-config)]
     (try
       (.createTopics ac [(NewTopic. ^String topic  (int partitions) (short replication))])
      ;; Ignore TopicExistsException, which would get thrown if the topic was previously created
       (catch TopicExistsException e nil)
       (finally
         (.close ac))))))

(defn log-ex [e]
  (log/error "Failed to deliver message" {} :cause e))
(defn log-metadata [metadata]
  (log/info (format "Produced record to topic %s partition [%d] @ offest %d"
                    (.topic metadata)
                    (.partition metadata)
                    (.offset metadata)) {}))

(defn create-record [topic key value]
  (ProducerRecord. topic key value))

(defn create-producer [props]
  (KafkaProducer. props))

(def callback (reify Callback
                (onCompletion [this metadata exception]
                  (if exception
                    (log-ex exception)
                    (log-metadata metadata)))))

(defn sender-fn [producer name]
  (fn sf
    ([e] (sf (:id e) e))
    ([k v] (.send producer (create-record name (str k) (pr-str v))) callback)))

(defn create-producer-channels [channels]
  (let [names (map name channels)
        props (kc/build-properties producer-props)
        producer (create-producer props)]
    (doseq [name names]
      (create-topic! name 1 3 props))
    (cc/closeable :producer
                  {:producer producer
                   :channels (->> channels
                                  (map #(do [% (sender-fn producer (name %))]))
                                  (into {}))}
                  (fn [v] (-> v :producer (.close))))))

