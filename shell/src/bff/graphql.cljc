(ns bff.graphql
  (:require [com.walmartlabs.lacinia.util :refer [inject-resolvers]]
            [com.walmartlabs.lacinia.schema :as ls]
            [com.walmartlabs.lacinia :refer [execute-parsed-query-async]]
            [com.walmartlabs.lacinia.resolve :as resolve]
            [com.walmartlabs.lacinia.parser :as parser]
            [clojure.data.json :as json]
            [manifold.deferred :as d]
            [clojure.java.io :as io]
            [com.kroo.epilogue :as log])
  (:import [java.lang Throwable]))

(defn to-http [result]
  {:status 200
   :headers {"Content-Type" "application/json"}
   :body (json/write-str result)})

(defn create-graphql-resolver [ctype command-sender]
  (fn [_ a _]
    (log/info (str "graphql resolver called " ctype) {:args a :mutation ctype})
    (let [cmd (assoc a :type ctype)
          pr (resolve/resolve-promise)
          dr (command-sender cmd)
          _ (d/on-realized dr
                           (fn [x]
                             (log/info (str "graphql resolver result " (:type x) " " (:message x) " " (= (:type x) :error)) {:result x :mutation ctype})
                             (if (= (:type x) :error)
                               (resolve/deliver! pr nil x)
                               (resolve/deliver! pr x)))
                           (fn [x]
                             (log/error (str "graphql resolver error " ctype) {:mutation ctype :command cmd} :cause x)
                             (resolve/with-error pr x)))]
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
        compiled (compile-schema schema gql-resolver-map)]
    (fn [req]
      (log/info "graphql handler request" {:request req})
      (try
        (let [body (-> req :body io/reader (json/read {:key-fn keyword}))
              query (get-in body [:query])
              vars (get-in body [:variables])
              parsed (parser/parse-query compiled query nil)
              d (d/deferred)
              result (execute-parsed-query-async parsed vars {})]
          (resolve/on-deliver! result
                               (fn [x]
                                 (log/info "graphql handler return" {:data (str x)})
                                 (d/success! d (to-http x))))
          d)
        (catch Throwable e
          (log/error "Error in gql handler" {:request req} :cause e))))))
