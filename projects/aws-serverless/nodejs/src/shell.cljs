(ns shell
  (:require [clojure.string :as str]
            [dynamodb-tools :as ddb]
            [promesa.core :as p]
            ["@redis/client" :as redis]
            [common.base.core :as core]))

(defn -js->clj+
  "For cases when built-in js->clj doesn't work. Source: https://stackoverflow.com/a/32583549/4839573"
  [x]
  (into {} (for [k (js-keys x)]
             [k (aget x k)])))

(defn env
  "Returns current env vars as a Clojure map."
  []
  (-js->clj+ (.-env js/process)))

(defn js->kwclj [j]
  (some-> j (js->clj :keywordize-keys true)))

(defn json->clj [j]
  (some-> j js/JSON.parse js->kwclj))

(defn clj->json [c]
  (some-> c (clj->js) js/JSON.stringify))

(defn create-redis-client []
  ;(js/console.log "SSSSSSSSSSSSSS" (clj->json (env)))
  (doto (redis/createClient (clj->js {:socket {:host (get (env) "REDIS_HOST") ;"response-queues-gwjcas.serverless.euw1.cache.amazonaws.com"
                                               :port (-> (env)
                                                         (get "REDIS_PORT")
                                                         (int)) ;6379
                                               :tls true}}))
    (.on "error" (fn [e] (js/console.error "Error" e)))
    (.connect)))

(defn pr> [x] (js/console.log ">" x) x)

(defn create-event-notify-ch [correlation-id]
  (let [q-client (create-redis-client)
        f #(do
             (js/console.log "Publish " correlation-id %)
             (.publish q-client correlation-id %))]
    (fn [e]
      (js/console.log "Notify " correlation-id (clj->js e))
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

(defn- subscribe-response [id ctx resolver]
  (let [events (atom [])
        q-client (create-redis-client)]
    (js/console.log "Subscribing to response queue " id " " q-client)
    (js/Promise.
     (fn [resolve _reject]
       (.subscribe q-client id
                   (fn [msg _]
                     (let [{:keys [type] :as v} (json->clj msg)]
                       (js/console.log "Received message" v)
                       (swap! events conj (assoc v :type (keyword type)))
                       (-> (.resolve js/Promise (resolver ctx @events))
                           (.then (fn [res]
                                    (js/console.log "Received response" (clj->js res))
                                    (when res
                                      (.unsubscribe q-client id)
                                      (.unref q-client)
                                      (resolve (clj->js res)))))))))))))

(defn write-command [command-name body init-ctx resolver] (js/console.log "Starting command" command-name body)
  (let [id (core/uuid)
        _ (js/console.log "Writing command" id command-name (clj->js body))
        command (-> body
                    (assoc :type command-name :event-id id)
                    (ddb/create-event-record "commands"))
        _ (js/console.log "Command" (clj->js command))
        db-client (ddb/create-client)
        ctx (assoc init-ctx :db db-client)
        response-promise (subscribe-response id ctx resolver)]
    (-> (.all js/Promise
              [response-promise
               (ddb/write-all-table-requests db-client [(ddb/create-table-put-requests "events" [command])])])
        (.then #(first %)))

    ;; (-> (do
    ;;       (js/console.log "Start dynamo")
    ;;       (-> (ddb/write-all-table-requests db-client [(ddb/create-table-put-requests "events" [command])])
    ;;           (pr>)))
    ;;     ;(.then (fn [_] ))
    ;;     (.catch (fn [e] (js/console.log "Error writing command" e)))
    ;;     (.finally #(do
    ;;                  (.destroy db-client)
    ;;                  (.unsubscribe q-client)
    ;;                  (.unref q-client)
    ;;                  (js/console.log "DONE"))))
    ;response-promise
    ))

(defn create-handler [handler-func lookup-func writer-func]
  (fn [e _ctx]
    (js/console.log "Event received " e)
    (p/let [db-client (ddb/create-client)
            q-client (create-redis-client)
            event (transalate-event e)
            correlation-id (:correlation-id event)
            out-topic (handler-topic handler-func)
            event-notify-ch (create-event-notify-ch correlation-id)
            ctx {:event-notify-ch event-notify-ch :db db-client}

            lookup-data (if lookup-func (lookup-func ctx event) {})
            _ (js/console.log "Lookup " (pr-str lookup-data))
            handler-output (handler-func ctx event lookup-data)
            result (some-> handler-output
                           (assoc-if :correlation-id (when out-topic correlation-id)))
            _ (js/console.log "Result " (pr-str result))
            writer-req (when writer-func
                         (writer-func ctx result))
            table-requests (when result
                             (->> [writer-req
                                   (when out-topic
                                     (ddb/create-table-put-requests "events" [(ddb/create-event-record result out-topic)]))]
                                  (remove nil?)
                                  (vec)))
            _ (js/console.log "Table requests " (pr-str table-requests))
            write-response (when (seq table-requests)
                             (ddb/write-all-table-requests db-client table-requests))
            _ (js/console.log "Write response " (pr-str write-response))]
      (event-notify-ch (meta writer-req))
      (event-notify-ch result)
      (.unref q-client)
      (.destroy db-client)
      (http-response 200 result))))

(defn create-lookup-writer-handler [handler-func lookup-func writer-func]
  (create-handler handler-func lookup-func writer-func))



;; 2024-05-01T17:07:56.672+01:00	2024-05-01T16:07:56.672Z f24c6ddf-b215-40d2-b85e-cddba52aa12c INFO DynamoDB debug endpoints Resolved endpoint: { "headers": {}, "properties": {}, "url": "https://dynamodb.eu-west-1.amazonaws.com/" }
;; 2024-05-01T17:07:56.991+01:00	2024-05-01T16:07:56.991Z f24c6ddf-b215-40d2-b85e-cddba52aa12c INFO DynamoDB info { clientName: 'DynamoDBClient', commandName: 'BatchWriteItemCommand', input: { RequestItems: { events: [Array] } }, output: { UnprocessedItems: {} }, metadata: { httpStatusCode: 200, requestId: 'N5881TE3OG42SVJ8TM2K8Q93EBVV4KQNSO5AEMVJF66Q9ASUAAJG', extendedRequestId: undefined, cfId: undefined, attempts: 1, totalRetryDelay: 0 } }
;; 2024-05-01T17:07:56.991+01:00	2024-05-01T16:07:56.991Z f24c6ddf-b215-40d2-b85e-cddba52aa12c INFO Sent command { '$metadata': { httpStatusCode: 200, requestId: 'N5881TE3OG42SVJ8TM2K8Q93EBVV4KQNSO5AEMVJF66Q9ASUAAJG', extendedRequestId: undefined, cfId: undefined, attempts: 1, totalRetryDelay: 0 }, UnprocessedItems: {} }
;; 2024-05-01T17:07:56.991+01:00	2024-05-01T16:07:56.991Z f24c6ddf-b215-40d2-b85e-cddba52aa12c INFO Resolver response undefined