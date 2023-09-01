(ns bff.graphql
  (:require [com.walmartlabs.lacinia.util :refer [inject-resolvers]]
            [com.walmartlabs.lacinia.schema :as ls]
            [com.walmartlabs.lacinia :refer [execute]]
            [clojure.data.json :as json]))

(defn handler [schema request]
  {:status 200
   :headers {"Content-Type" "application/json"}
   :body (let [query (get-in request [:query-params :query])
               result (execute schema query nil nil)]
           (json/write-str result))})

(defn compile [schema resolvers]
  (-> schema
      (inject-resolvers resolvers)
      ls/compile))