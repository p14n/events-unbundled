(ns core
  (:require [async.core :as ac]
            [clojure.core.async :as a]
            [common.core :as c]
            [bff.cache :as bff]
            [simple-db.core :as sdb]))


(defn create-system [handlers resolvers]
  (fn [do-with-state]
    (with-open [channels (->> (conj (c/get-all-channel-names handlers) :commands :notify)
                              ac/create-all-channels-closable)
                db (c/closeable :db (atom {}) #(reset! % {}))
                st (c/closeable :state {:channels @channels
                                        :command-sender (bff/create-command-sender
                                                         (c/map-command-type-to-resolver resolvers)
                                                         (fn [cmd] (a/put! (:commands @channels) cmd
                                                                           (fn [a] (println "Command sent " a cmd)))))
                                        :notify-ch (fn [ev res]
                                                     (a/put! (:notify @channels) (assoc res :res-corr-id (:res-corr-id ev)))
                                                     nil)
                                        :db @db})
                system (ac/start-system @st handlers @channels bff/responder-executor)]
      (do-with-state @st))))

(def with-system
  (create-system [sdb/invite-customer-simple-db
                  sdb/project-customer-to-simple-db]
                 [sdb/invite-response]))

(def state (atom nil))
(defonce instance (atom (future ::never-run)))

(defn get-db []
  (-> @state :db deref))

(defn send-command [cmd]
  ((-> @state :command-sender) cmd))

(def start (c/start-fn instance #(with-system (c/publishing-state c/forever state))))
(def stop (c/stop-fn instance))

