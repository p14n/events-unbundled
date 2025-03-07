(ns clj.shell
  (:require ["@dbos-inc/dbos-sdk" :as dbos]
            [promesa.core :as p]))

(defn sqlclient
  ([sql & args]
   (let [r (.raw (.-knexClient dbos/DBOS) sql (clj->js args))]
     (js/Promise. (fn [resolve _reject]
                    (.then r resolve))))))

(defn send [m]
  (when m
    (let [wid (.-workflowID dbos/DBOS)]
      (.send dbos/DBOS wid (clj->js m) "notify")
      nil)))

(def ctx {:db sqlclient
          :event-notify-ch identity})

(defn js->clj-keywordize [m]
  (js->clj m :keywordize-keys true))

(defn js->clj-event [e]
  (let [{:keys [type] :as ek} (js->clj-keywordize e)]
    (if type
      (assoc ek :type (keyword type))
      ek)))

;; (defn make-handler [handler]
;;   (with-meta (fn [event lookup]
;;                (clj->js (handler ctx
;;                                  (js->clj-event event)
;;                                  (js->clj-keywordize lookup))))
;;     (meta handler)))

(defn make-handler [handler]
  (fn [event lookup]
    (clj->js (handler ctx
                      (js->clj-event event)
                      (js->clj-keywordize lookup)))))

(defn make-lookup [lookup]
  (fn [event]
    (p/let [l (lookup ctx (js->clj-event event))]
      (clj->js l))))

(defn make-write [write]
  (fn [event]
    (write ctx (js->clj-event event))
    (clj->js event)))

(defn rows->clj [result]
  (let [rows (.-rows result)]
    (js->clj-keywordize rows)))
