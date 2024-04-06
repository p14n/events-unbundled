(ns shell
  (:require [handlers :as h]))

(defn create-handler [handler-func]
  (fn [e _ctx]
    (let [ctx {:event-notify-ch #(some-> % clj->js js/console.log)}
          event (-> e (js->clj :keywordize-keys true) (update :type keyword))
          result (handler-func ctx event {})]
      (js/Promise.resolve
       (clj->js {:statusCode 200
                 :body       (js/JSON.stringify
                              (clj->js result))})))))

#js {:inviteCustomer (create-handler h/invite-customer)}