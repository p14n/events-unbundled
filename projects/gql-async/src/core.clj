(ns core
  (:require [async.core :as ac]
            [clojure.core.async :as a]
            [handlers :as h]
            [projectors :as p]
            [resolvers :as r]
            [common.core :as c]
            [bff.cache :as bff]
            [bff.http :as http]
            [bff.graphql :as gql]))


(def schema
  '{:objects

    {:Customer {:fields
                {:id {:type ID}
                 :email {:type String}
                 :invited {:type Boolean}}}}
    :mutations
    {:InviteCustomer {:type :Customer
                      :description "Invite a customer"
                      :args {:email {:type String}}}}})

;; (defn name->path
;;   "Given a key, such as :queries/user_by_id or :User/full_name, return the
;;   path from the root of the schema to (and including) the path-ex key."
;;   [schema k path-ex]
;;   (let [container-name (-> k namespace keyword)
;;         field-name (-> k name keyword)
;;         path (if (operation-containers container-name)
;;                [container-name field-name]
;;                [:objects container-name :fields field-name])]
;;     (when-not (get-in schema path)
;;       (throw (ex-info "inject error: not found"
;;                       {:key k})))

;;     (conj path path-ex)))

(defn create-system [handlers resolvers]
  (fn [do-with-state]
    (with-open [channels (->> (conj (ac/get-all-channel-names handlers) :commands :notify)
                              ac/create-all-channels-closable)
                db (c/closeable (atom {}) #(reset! % {}))
                st (c/closeable {:channels @channels
                                 :command-sender (bff/create-command-sender
                                                  (c/map-command-type-to-resolver resolvers)
                                                  (fn [cmd] (a/put! (:commands @channels) cmd)))
                                 :notify-ch (fn [ev res]
                                              (a/put! (:notify @channels) (assoc res :res-corr-id (:res-corr-id ev)))
                                              nil)
                                 :db @db})
                http (http/start-http (gql/create-gql-handler schema resolvers (:command-sender @st)))
                system (ac/start-system @st handlers @channels bff/responder)]
      (do-with-state @st))))

(def with-system
  (create-system [h/invite-customer
                  p/project-customer-to-simple-db]
                 [r/invite-response]))

(def state (atom nil))
(def instance (atom (future ::never-run)))

(def start (c/start-fn instance #(with-system (c/publishing-state c/forever state))))
(def stop (c/stop-fn instance))

