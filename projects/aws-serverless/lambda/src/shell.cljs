(ns shell)

(defn create-handler [handler-func lookup-func writer-func]
  (fn [e _ctx]
    (let [ctx {:event-notify-ch #(some-> % clj->js js/console.log)}
          event (-> e (js->clj :keywordize-keys true) (update :type keyword))
          lookup-data (if lookup-func (lookup-func ctx event) {})
          result (handler-func ctx event lookup-data)
          ;write event out
          ]
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



