(ns lambdas
  (:require [handlers :as h]
            [lookups :as l]
            [shell :as s]))



#js {(s/handler-name-kw h/invite-customer) (s/create-lookup-writer-handler h/invite-customer l/invite-customer-lookup nil)}


