(ns xtdb.core
  (:require [xtdb.api :as xt]
            [kafka.common :as kcm]
            [common.core :as cc])
  (:import [xtdb.api IXtdb]))

(defn node-properties [groupid]
  (let [pm (->> {"group.id" groupid}
                (kcm/build-properties)
                (into {}))]
    {:xtdb.kafka/kafka-config {:bootstrap-servers (get pm "bootstrap.servers") ; replace with value from your properties file
                               :properties-map pm} ; replace with the path of your properties file
     :xtdb/tx-log {:xtdb/module 'xtdb.kafka/->tx-log
                   :kafka-config :xtdb.kafka/kafka-config
                   :tx-topic-opts {:topic-name "tx-topic1" ; choose your tx-topic name
                                   :replication-factor 3}} ; Confluent Cloud requires this to be at least `3`
     :xtdb/document-store {:xtdb/module 'xtdb.kafka/->document-store
                           :kafka-config :xtdb.kafka/kafka-config
                           :doc-topic-opts {:topic-name "doc-topic1" ; choose your document-topic name
                                            :replication-factor 3}}}))

(defn start-node ^IXtdb [groupid]
  (cc/closeable (keyword (str "xtdb-" groupid))
                (xt/start-node (node-properties groupid))
                (fn [db]
                  (.close db))))