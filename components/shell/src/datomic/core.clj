(ns datomic.core
  (:require [datomic.api :as d]
            [common.core :as cc]))

(def db-uri "datomic:mem://customer")

(defn create-db [schema]
  (cc/closeable :datomic
                (do (d/create-database db-uri)
                    (let [conn (d/connect db-uri)]
                      (d/transact conn schema)
                      conn))
                (fn [conn] (.release conn))))

