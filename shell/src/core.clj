(ns core
  (:require [aleph.http :as http]
            [manifold.deferred :as d]))

(def cache (atom {}))
(def running (atom false))

(defn infinite-loop [function]
  (function)
  (println "looping")
  (if @running
    (future (infinite-loop function)))
  nil)

;; note the nil above is necessary to avoid overflowing the stack with futures...

(defn response [id]
  {:status 200
   :headers {"content-type" "text/plain"}
   :body (str "hello " id)})

(defn respond-from-cache []
  (doall (map (fn [[id {:keys [d]}]]
                (do (d/success! d (response id))
                    (swap! cache #(dissoc % id)))) @cache)))

(defn handler [req]
  (let [id (str (rand-int 1000000))
        d (d/deferred)]
    (swap! cache assoc id {:d d})
    d))

(defn start []
  (let [_ (reset! running true)
        s (http/start-server handler {:port 8080})
        _ (infinite-loop #(do
                            (Thread/sleep 5000)
                            (respond-from-cache)))]
    #(do (.close s)
         (reset! running false))))



; Get request
; Create ID
; Add ID and deferred to cache
; Send request to service
; Service updates kafka
; presentation builds model
; cache listens to kafka model stream
; cache populates deferred 

