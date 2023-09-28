(ns postgres.core
  (:require [next.jdbc :as jdbc])
  (:import (io.zonky.test.db.postgres.embedded EmbeddedPostgres)))

(defn get-connection [postgres]
  (-> postgres  (.getPostgresDatabase) (.getConnection)))

(defn setup-schema [cn]
  (jdbc/execute! cn ["CREATE TABLE IF NOT EXISTS customers (id VARCHAR(40), email VARCHAR(255) PRIMARY KEY, invited BOOLEAN NOT NULL)"]))

(defn start-postgres []
  (let [postgres (EmbeddedPostgres/start)]
    (setup-schema (get-connection postgres))
    (println "Started Postgres on port" (.getPort postgres))
    postgres))


