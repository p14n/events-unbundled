(ns test-steps
  (:require [clojure.test :refer :all]
            [lambdaisland.cucumber.dsl :refer :all]
            [core-test :as sut]
            [clojure.string :as str]
            [clojure.data.json :as json]
            [clojure.edn :as edn]
            [org.httpkit.client :as http]))

(declare any)


(defn replace-val
  [text state match]
  (let [name (second match)
        replacement (get state name)]
    (if replacement
      (str/replace text (first match) replacement)
      text)))

(defn replace-variables
  [text state]
  (let [vars (re-seq #"\$\{([^{}]*)\}" text)]
    (reduce #(replace-val %1 state %2) text vars)))

(defn perform-query-inner
  [query variables auth-headers]
  (let [reqbody (str (json/write-str {:query query :variables variables}))
        opts {:headers (merge auth-headers {"Content-Type"   "application/json"
                                            "Content-Length" (.length reqbody)})
              :body    reqbody}]
    @(http/post "http://localhost:8080/" opts)))

(defn perform-query-returning-body
  [query variables auth-headers]
  (let [{:keys [body] :as resp} (perform-query-inner query variables auth-headers)
        _ (if (not (= 200 (resp :status)))
            (println "Response was not 200" resp))]
    (json/read-str body :key-fn keyword :bigdec true)))

(defn resolve-function
  [sym-or-str]
  (let [r (resolve sym-or-str)]
    (if r r (resolve (symbol (str "test-steps/" sym-or-str))))))

(def resolve-path)

(defn apply-path-fn
  [[f & fs :as path-fns] response expected]
  (if-not (seq path-fns)
    ;; Finished exploring paths
    response
    (if-not (= #'any f)
      ;; A normal path func, just apply it
      (f response)
      ;; We're looking in all paths of a sequence
      (some
       ;; Return the element at the index where we found the expected value
       (fn [[idx elem]] (when (= expected (str elem)) (response idx)))
       ;; Apply all the paths below this one over the whole sequence
       (map-indexed
        (fn [idx elem] [idx (resolve-path fs elem expected)])
        response)))))

(defn tails
  [xs]
  (if-not
   (seq xs) '(())
   (cons xs (lazy-seq (tails (rest xs))))))

(defn resolve-path
  [path-fns response expected]
  (reduce #(apply-path-fn %2 %1 expected) response (tails path-fns)))

(defn try-to-resolve
  [string-value]
  (if (keyword? string-value) string-value (resolve-function string-value)))


(defn path-string-to-resolved
  [path]
  (->> path
       (edn/read-string)
       (map try-to-resolve)
       (vec)))



(Then "the response value at (.*) is" [{:keys [response] :as state} path expected]
      (let [path-fns (path-string-to-resolved path)
            at-path (resolve-path path-fns response expected)]
        (is (= (replace-variables expected state) (str at-path)))
        state))

(When "I use the query" [state doc-string]
      (let [replaced (replace-variables doc-string state)
            response (perform-query-returning-body replaced nil (:auth state))]
        (assoc state :response response)))

(Before ["@system"] (sut/start-test-system))
(After ["@system"] (sut/stop-test-system))


