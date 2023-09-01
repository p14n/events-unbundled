(ns bff.cache
  (:require [manifold.deferred :as d]))

(def cache (atom {}))
(def running (atom false))

(defn infinite-loop [function]
  (function)
  (println "looping")
  (if @running
    (future (infinite-loop function)))
  nil)

(defn response [id]
  {:status 200
   :headers {"content-type" "text/plain"}
   :body (str "hello " id)})

(defn respond-from-cache []
  (doall (map (fn [[id {:keys [d]}]]
                (do (d/success! d (response id))
                    (swap! cache #(dissoc % id)))) @cache)))

(defn cache-handler [req]
  (let [id (str (rand-int 1000000))
        d (d/deferred)]
    (swap! cache assoc id {:d d})
    d))

(defn start-cache []
  (let [_ (reset! running true)
        _ (infinite-loop #(do
                            (Thread/sleep 5000)
                            (respond-from-cache)))]
    #(reset! running false)))