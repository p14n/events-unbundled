(ns shell
  (:require [clojure.string :as str]
            [dynamodb-tools :as ddb]
            [promesa.core :as p]))

(defn event-notify-ch [e]
  (some-> e clj->js js/JSON.stringify js/console.log))

(defn assoc-if [m k v]
  (if (and m k v)
    (assoc m k v)
    m))


(defn handler-name [handler]
  (-> handler meta :name name (str/replace "-" "") (str/replace "_" "")))

(defn handler-topic [handler]
  (some-> handler meta :out name))

(defn handler-name-kw [handler]
  (-> handler handler-name keyword))

(defn http-response [status body]
  (js/Promise.resolve
   (clj->js {:statusCode status
             :body       (js/JSON.stringify
                          (clj->js body))})))

(defn transalate-event [e]
  (let [event-detail (-> e.detail (js->clj :keywordize-keys true) (update :type keyword))
        event (-> event-detail
                  (dissoc :body)
                  (merge (->
                          (get-in event-detail [:body])
                          (js/JSON.parse)
                          (js->clj :keywordize-keys true))))]
    event))

(defn create-handler [handler-func lookup-func writer-func]
  (fn [e _ctx]
    (js/console.log "Event received " e)
    (let [ctx {:event-notify-ch event-notify-ch}
          out-topic (handler-topic handler-func)
          client (ddb/create-client)
          event (transalate-event e)
          lookup-data (if lookup-func (lookup-func ctx event) {})
          result (assoc-if (handler-func ctx event lookup-data)
                           :correlation-id
                           (when out-topic (:correlation-id event)))
          table-requests (->> [(when writer-func (writer-func ctx result))
                               (when (and result out-topic)
                                 (ddb/create-table-request "events" [(ddb/create-event-record result out-topic)]))]
                              (remove nil?)
                              (vec))
          _ (js/console.log "Result " (pr-str result))
          _ (js/console.log "Table requests " (pr-str table-requests))]
      (if (seq table-requests)
        (p/let [write-response (ddb/write-all-table-requests client table-requests)]
          (event-notify-ch result)
          (http-response 200 result))
        (http-response 200 result)))))

;; (defn create-simple-handler [handler-func]
;;   (create-handler handler-func nil nil))

;; (defn create-lookup-handler [handler-func lookup-func]
;;   (create-handler handler-func lookup-func nil))

(defn create-lookup-writer-handler [handler-func lookup-func writer-func]
  (create-handler handler-func lookup-func writer-func))



