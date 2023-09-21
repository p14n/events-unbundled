(ns bff.cache
  (:require [manifold.deferred :as d]
            [com.kroo.epilogue :as log])
  (:import [java.lang Throwable]))

(def response-cache (atom {}))

(defn create-command-sender [resolvers command-ch]
  (fn [cmd]
    (log/info "Sending command" {:command cmd})
    (try
      (let [ctype (:type cmd)
            resolver (get resolvers ctype)
            id (str (rand-int 1000000))
            df (d/deferred)]
        (swap! response-cache assoc id {:d df :resolver resolver :events []})
        (command-ch (assoc cmd :res-corr-id id))
        df)
      (catch Throwable e
        (log/error "Error sending command" {:command cmd} :cause e)))))

(defn add-event-to-response-cache [id event]
  (swap! response-cache
         (fn [c]
           (let [r (c id)
                 u (assoc r :events (conj (:events r) (or (:response event) event)))]
             (assoc c id u)))))

(defn responder [ctx event]
  (log/info "Responder received event" {:event event})
  (let [id (or (:res-corr-id event) (-> event :event :res-corr-id))
        {:keys [d resolver events]} (get (add-event-to-response-cache id event) id)
        res (or
             (->> events (filter #(= (:type %) :error)) first)
             (resolver ctx events))
        _ (log/info "Responser returning" {:result res})]
    (when res
      (d/success! d res)
      (swap! response-cache dissoc id))))