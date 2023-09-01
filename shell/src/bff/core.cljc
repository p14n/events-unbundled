(ns bff.core
  (:require [aleph.http :as http]
            [bff.cache :as cache]))

(defn start-bff [handler]
  (let [s (http/start-server handler {:port 8080})
        cstop (cache/start-cache)]
    #(do (.close s)
         (cstop))))