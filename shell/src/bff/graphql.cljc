(ns bff.graphql
  (:require [com.walmartlabs.lacinia.util :refer [inject-resolvers]]
            [com.walmartlabs.lacinia.schema :as ls]
            [com.walmartlabs.lacinia :refer [execute-parsed-query-async]]
            [com.walmartlabs.lacinia.resolve :as resolve]
            [com.walmartlabs.lacinia.parser :as parser]
            [clojure.data.json :as json]
            [manifold.deferred :as d]
            [clojure.core.async :as a]
            [clojure.java.io :as io]))

(defn to-http [result]
  {:status 200
   :headers {"Content-Type" "application/json"}
   :body (json/write-str result)})

(defn create-graphql-resolver [ctype command-sender]
  (fn [_ a _]
    (let [cmd (assoc a :type ctype)
          pr (resolve/resolve-promise)
          dr (command-sender cmd)
          _ (d/on-realized dr (fn [x] (resolve/deliver! pr x)) identity)]
      pr)))

(defn compile-schema [schema resolvers]
  (-> schema
      (inject-resolvers resolvers)
      ls/compile))

(defn resolver-k-v [command-sender r]
  (let [t (-> r meta :type)
        k (->> t name (str "mutations/") keyword)]
    [k (create-graphql-resolver t command-sender)]))

(defn create-gql-handler [schema resolvers command-sender]
  (let [gql-resolver-map (->> resolvers
                              (map (partial resolver-k-v command-sender))
                              (into {}))
        _ (println gql-resolver-map)
        _ (println schema)
        compiled (compile-schema schema gql-resolver-map)]
    (fn [req]
      (let [body (-> req :body io/reader (json/read {:key-fn keyword}))
            query (get-in body [:query])
            _ (println req)
            _ (println body)
            _ (println query)
            vars (get-in body [:variables])
            _ (println vars)
            parsed (parser/parse-query compiled query nil)
            d (d/deferred)
            result (execute-parsed-query-async parsed vars {})]
        (resolve/on-deliver! result (fn [x] (d/success! d (to-http x))))
        d))))
