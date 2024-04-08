(ns dynamodb-tools
  (:require ["@aws-sdk/client-dynamodb" :as ddb]
            [common.base.core :as core]))

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

(defn create-table-request [table-name items]
  {table-name (mapv create-put-request items)})

(defn write-all-table-requests [client table-requests]
  (let [req (ddb/BatchWriteItemCommand. (clj->js {"RequestItems" (apply merge table-requests)}))]
    (.send client req)))

(defn create-client []
  (ddb/DynamoDBClient. {}))
