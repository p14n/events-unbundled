The structure of the system might look a little odd, its based on a [pattern](https://medium.com/@maciekszajna/reloaded-workflow-out-of-the-box-be6b5f38ea98) for safely starting and stopping a system.  Each var in the `with-open[...]` has its close method called when the function completes (in a production system, thats essentially never/`Thread.join()`)
```
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
```
We can see a number of things happening here
* We read the required channels from the handlers, and create them in core.async
* We create a naive in-memory database
* We create some global state; this includes some special functions
    * The notify channel that communicates a transient message to the caller via the responder cache (think 400 error messages)
    * The command sender that introduces commands into the system.  In this simple implementation, its our api.
     
The system uses core async to send events between components.  There is no http interface - usage can be seen in the [test](test/basic_async_test.clj).

Of note is the [responder cache](../../components/shell/src/bff/cache.cljc).  It's job is simply to correlate the events written to the system and match the request to the correct responder (or the caller would never get a response).