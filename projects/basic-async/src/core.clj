(ns core
  (:require [async.core :as ac]
            [handlers :as h]
            [common.core :as c]))

(defn create-system [handlers]
  (fn [do-with-state]
    (with-open [command (ac/create-closable-channel)
                events (ac/create-closable-channel)
                system (ac/start-system {} handlers @command @events)]
      (do-with-state {:command @command
                      :events @events}))))

(def with-system
  (create-system [h/invite-customer]))

(def state (atom nil))
(def instance (atom (future ::never-run)))

(def start (c/start-fn instance #(with-system (c/publishing-state c/forever state))))
(def stop (c/stop-fn instance))

