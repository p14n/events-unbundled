(ns bff.http
  (:require [aleph.http :as http]
            [common.core :as c]))

(defn start-http [handler]
  (let [s (http/start-server handler {:port 8080})]
    (c/closeable s #(.close %))))