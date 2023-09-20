(ns kafka.common
  (:require [clojure.java.io :as io]
            [clojure.string :as str])
  (:import
   (java.util Properties)
   (java.io StringReader)))

(defn build-properties [props-map]
  (with-open [config (-> "kafka/confluent-cloud.props"
                         (io/resource)
                         (slurp)
                         (str/replace "{{ BROKER_ENDPOINT }}" (System/getenv "BROKER_ENDPOINT"))
                         (str/replace "{{ CLUSTER_API_KEY }}" (System/getenv "CLUSTER_API_KEY"))
                         (str/replace "{{ CLUSTER_API_SECRET }}" (System/getenv "CLUSTER_API_SECRET"))
                         (StringReader.))]
    (doto (Properties.)
      (.putAll props-map)
      (.load config))))

