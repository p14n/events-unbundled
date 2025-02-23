(ns clj.shell
  (:require ["@dbos-inc/dbos-sdk" :as dbos]
            [promesa.core :as p]))

(defn sqlclient
  ([sql]
   (let [r (.raw (.-knexClient dbos/DBOS) sql)]
     (js/Promise. (fn [resolve _reject]
                    (.then r resolve)))))
  ([sql args]
   (let [r (.raw (.-knexClient dbos/DBOS) sql args)]
     (js/Promise. (fn [resolve _reject]
                    (.then r resolve))))))

(def db-ctx {:db sqlclient})

(defn make-handler [handler]
  (fn [event lookup]
    (clj->js (handler {}
                      (js->clj event :keywordize-keys true)
                      (js->clj lookup :keywordize-keys true)))))

(defn make-lookup [lookup]
  (fn [event]
    (p/let [l (lookup db-ctx (js->clj event :keywordize-keys true))]
      (clj->js l))))

(defn rows->clj [result]
  (let [rows (.-rows result)]
    (js->clj rows :keywordize-keys true)))

