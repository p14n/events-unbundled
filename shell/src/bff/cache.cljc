(ns bff.cache
  (:require [manifold.deferred :as d]))

(def response-cache (atom {}))

(defn create-command-sender [resolvers command-ch]
  (fn [cmd]
    (let [ctype (:type cmd)
          resolver (get resolvers ctype)
          id (str (rand-int 1000000))
          df (d/deferred)]
      (swap! response-cache assoc id {:d df :resolver resolver :events []})
      (command-ch (assoc cmd :res-corr-id id))
      df)))

(defn add-event-to-response-cache [id event]
  (swap! response-cache
         (fn [c]
           (let [r (c id)
                 u (assoc r :events (conj (:events r) (or (:response event) event)))]
             (assoc c id u)))))

(defn responder [ctx event]
  (let [id (or (:res-corr-id event) (-> event :event :res-corr-id))
        ;_ (println "Responder received event" event "with id" id)
        ;_ (println (@response-cache id))
        {:keys [d resolver events]} (get (add-event-to-response-cache id event) id)
        ;_ (println "Resolver run" id)
        res (resolver ctx events)
        ;_ (println "Resolver returned" res)
        ]
    (when res
      (d/success! d res)
      (swap! response-cache dissoc id))))