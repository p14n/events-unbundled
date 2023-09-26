(ns test-util)

(defonce before-promise (promise))
(defonce after-promise (promise))

(defn system-holder [wait-for-secs]
  (deliver before-promise true)
  (println "Before promise delivered")
  (deref after-promise (* wait-for-secs 1000) true))

(defn system-closer [] (deliver after-promise true))

(defn system-waiter []
  (deref before-promise 10000 true)
  (println "Before promise done waiting"))



