(ns bff.core
  (:require [aleph.http :as http]
            [bff.cache :as cache]))

(defn start-bff []
  (let [s (http/start-server cache/cache-handler {:port 8080})
        cstop (cache/start-cache)]
    #(do (.close s)
         (cstop))))