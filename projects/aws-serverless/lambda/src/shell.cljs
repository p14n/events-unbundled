(ns shell
  (:require [clojure.string :as str]))

(defn event-notify-ch [e]
  (some-> e clj->js js/console.log))

(defn assoc-if [m k v]
  (if (and m k v)
    (assoc m k v)
    m))


(defn handler-name [handler]
  (-> handler meta :name name (str/replace "-" "") (str/replace "_" "")))

(defn handler-name-kw [handler]
  (-> handler handler-name keyword))

(defn create-handler [handler-func lookup-func writer-func]
  (fn [e _ctx]
    (js/console.log "Event received " e)
    (let [ctx {:event-notify-ch event-notify-ch}
          event-detail (-> e.detail (js->clj :keywordize-keys true) (update :type keyword))
          event (-> event-detail
                    (dissoc :body)
                    (merge (->
                            (get-in event-detail [:body])
                            (js/JSON.parse)
                            (js->clj :keywordize-keys true))))
          lookup-data (if lookup-func (lookup-func ctx event) {})
          result (assoc-if (handler-func ctx event lookup-data)
                           :correlation-id
                           (:correlation-id event))
          ;write event out
          ]
      (event-notify-ch result)
      (when writer-func
        (writer-func ctx result))
      (js/Promise.resolve
       (clj->js {:statusCode 200
                 :body       (js/JSON.stringify
                              (clj->js result))})))))

;; (defn create-simple-handler [handler-func]
;;   (create-handler handler-func nil nil))

;; (defn create-lookup-handler [handler-func lookup-func]
;;   (create-handler handler-func lookup-func nil))

(defn create-lookup-writer-handler [handler-func lookup-func writer-func]
  (create-handler handler-func lookup-func writer-func))



