(ns core
  (:require [async.core :as ac]
            [handlers :as h]
            [projectors :as p]
            [common.core :as c]))


(defn create-system [handlers]
  (fn [do-with-state]
    (with-open [channels (->> handlers
                              ac/get-all-channel-names
                              ac/create-all-channels-closable)
                db (c/closeable (atom {}) #(reset! % {}))
                notify (c/closeable (fn [ev res]))
                system (ac/start-system {:db @db :notify-ch @notify} handlers @channels)]
      (do-with-state {:channels @channels
                      :notify-ch @notify
                      :db @db}))))

(def with-system
  (create-system [h/invite-customer
                  p/project-customer-to-simple-db]))

(def state (atom nil))
(def instance (atom (future ::never-run)))

(def start (c/start-fn instance #(with-system (c/publishing-state c/forever state))))
(def stop (c/stop-fn instance))

