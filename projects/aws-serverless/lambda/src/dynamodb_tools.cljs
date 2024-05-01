(ns dynamodb-tools
  (:require ["@aws-sdk/client-dynamodb" :as ddb]
            [common.base.core :as core]
            [promesa.core :as p]))

(defn create-put-request [item]
  {"PutRequest" {"Item" item}})

(defn create-event-record [event topic]
  (let [{:keys [event-id correlation-id type created]} event
        body (dissoc event :event-id :correlation-id :type)
        eid (or event-id (core/uuid))]
    {"event-id" {"S" eid}
     "correlation-id" {"S" (or correlation-id eid)}
     "type" {"S" (some-> type name)}
     "created" {"S" (or created (.toISOString (js/Date.)))}
     "topic" {"S" topic}
     "body" {"S" (-> body clj->js js/JSON.stringify)}}))

(defn create-table-put-requests [table-name items]
  {table-name (mapv create-put-request items)})

(defn write-all-table-requests [client table-requests]
  (js/console.log "Writing all table requests " table-requests " to " client)
  (let [req (ddb/BatchWriteItemCommand. (clj->js {"RequestItems" (apply merge table-requests)}))]
    (js/console.log "Request " req)
    (.send client req)))

(defn create-get-item-command [table-name key]
  (let [key (clj->js key)]
    (ddb/GetItemCommand. (clj->js {"TableName" table-name "Key" key}))))


(defn create-client []
  (ddb/DynamoDBClient. (clj->js {:logger  {:error (fn [l] (js/console.log "DynamoDB error" l))
                                           :info (fn [l] (js/console.log "DynamoDB info" l))
                                           :warning (fn [l] (js/console.log "DynamoDB warning" l))
                                           :trace (fn [l])
                                           :debug (fn [l] (js/console.log "DynamoDB debug " l))}})))

(defn result-item [result]
  (some-> result (js->clj :keywordize-keys true) :Item))

(defn item->object [item]
  (->> item
       (map (fn [[k v]] [k (some-> v first last)]))
       (into {})))

(defn result->object [result]
  (-> result result-item item->object))

(defn single-item-fetch [db table id]
  (let [cmd (create-get-item-command table id)]
    (p/-> (.send db cmd) result->object)))
