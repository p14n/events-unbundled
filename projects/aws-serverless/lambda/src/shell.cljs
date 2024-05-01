(ns shell
  (:require [clojure.string :as str]
            [dynamodb-tools :as ddb]
            [promesa.core :as p]
            ["@redis/client" :as redis]
            [common.base.core :as core]))

(defn create-redis-client []
  (doto (redis/createClient (clj->js {:socket {:host "response-queues-gwjcas.serverless.euw1.cache.amazonaws.com"
                                               :port 6379
                                               :tls true}}))
    (.on "error" (fn [e] (js/console.error "Error" e)))
    (.connect)))


(defn create-event-notify-ch [correlation-id]
  (p/let [client (create-redis-client)
          f #(.publish client correlation-id %)]
    (fn [e]
      (some-> e clj->js js/JSON.stringify f)
      nil)))

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

(defn- subscribe-response [ch id ctx resolver]
  (p/let [events (atom [])
          q-client (create-redis-client)]
    (js/console.log "Subscribing to response queue " id " " q-client)
    (.subscribe q-client id
                (fn [msg _]
                  (let [v (js->clj (js/JSON.parse msg))]
                    (js/console.log "Received message" v)
                    (swap! events conj v)
                    (p/let [res (resolver ctx @events)]
                      (when res
                        (.unsubscribe q-client id)
                        (p/resolve! ch res))))))))

(defn write-command [command-name body {:keys [db] :as ctx} resolver]
  (try
    (js/console.log "Starting command" command-name body)
    (let [ch (p/deferred)]
      (p/let [id (core/uuid)
              _ (js/console.log "Writing command" id command-name (clj->js body))
              command (-> body
                          (assoc :type command-name)
                          (ddb/create-event-record "commands"))
              _ (subscribe-response ch id ctx resolver)
              _ (js/console.log "Subscribed to response queue" id)
              _ (js/console.log "Command" (clj->js command))
              command-response (ddb/write-all-table-requests db [(ddb/create-table-put-requests "events" [command])])
              _ (js/console.log "Sent command" command-response)
              res (deref ch 10000 :timeout)
              _ (js/console.log "Resolver response " res)]

        res))
    (catch js/Error e
      (js/console.log "Error writing command" e)
      (js/console.trace e)
      (.toString e))))

(defn create-handler [handler-func lookup-func writer-func]
  (fn [e _ctx]
    (js/console.log "Event received " e)
    (p/let [client (ddb/create-client)
            event (transalate-event e)
            correlation-id (:correlation-id event)
            out-topic (handler-topic handler-func)
            event-notify-ch (create-event-notify-ch correlation-id)
            ctx {:event-notify-ch event-notify-ch :db client}

            lookup-data (if lookup-func (lookup-func ctx event) {})
            _ (js/console.log "Lookup " (pr-str lookup-data))

            result (some-> (handler-func ctx event lookup-data)
                           (assoc-if :correlation-id (when out-topic correlation-id)))
            _ (js/console.log "Result " (pr-str result))
            table-requests (when result
                             (->> [(when writer-func
                                     (writer-func ctx result))
                                   (when out-topic
                                     (ddb/create-table-put-requests "events" [(ddb/create-event-record result out-topic)]))]
                                  (remove nil?)
                                  (vec)))
            _ (js/console.log "Table requests " (pr-str table-requests))
            write-response (when (seq table-requests)
                             (ddb/write-all-table-requests client table-requests))
            _ (js/console.log "Write response " (pr-str write-response))]
      (event-notify-ch result)
      (http-response 200 result))))

;; (defn create-simple-handler [handler-func]
;;   (create-handler handler-func nil nil))

;; (defn create-lookup-handler [handler-func lookup-func]
;;   (create-handler handler-func lookup-func nil))

(defn create-lookup-writer-handler [handler-func lookup-func writer-func]
  (create-handler handler-func lookup-func writer-func))


;; 2024-04-30T22:42:27.019+01:00	2024-04-30T21:42:27.019Z 34e0cfd7-4846-44ff-8d1e-668c866ce25f INFO Lookup {:existing-id "61e8be12-fbbf-4688-9677-54910f6331e9"}
;; 2024-04-30T22:42:27.079+01:00
;; 2024-04-30T21:42:27.079Z	34e0cfd7-4846-44ff-8d1e-668c866ce25f	ERROR	Invoke Error 	
;; {
;;     "errorType": "Error",
;;     "errorMessage": "No protocol method IAssociative.-assoc defined for type object: [object Promise]",
;;     "message": "No protocol method IAssociative.-assoc defined for type object: [object Promise]",

