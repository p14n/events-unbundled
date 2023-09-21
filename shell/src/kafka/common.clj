(ns kafka.common
  (:require [clojure.java.io :as io]
            [clojure.string :as str])
  (:import
   (java.util Properties)
   (java.io StringReader)))

(defn build-properties [props-map]
  (let [be (System/getenv "BROKER_ENDPOINT")
        ak (System/getenv "CLUSTER_API_KEY")
        as (System/getenv "CLUSTER_API_SECRET")]
    (if (and be ak as)
      (with-open [config (-> "kafka/confluent-cloud.props"
                             (io/resource)
                             (slurp)
                             (str/replace "{{ BROKER_ENDPOINT }}" be)
                             (str/replace "{{ CLUSTER_API_KEY }}" ak)
                             (str/replace "{{ CLUSTER_API_SECRET }}" as)
                             (StringReader.))]
        (doto (Properties.)
          (.putAll props-map)
          (.load config)))
      (throw (Exception. "Missing environment variables for Kafka BROKER_ENDPOINT, CLUSTER_API_KEY, CLUSTER_API_SECRET")))))

