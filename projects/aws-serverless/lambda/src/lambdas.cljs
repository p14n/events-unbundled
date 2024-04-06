(ns lambdas
  (:require [handlers :as h]
            [lookups :as l]
            [shell :as s]))



#js {:inviteCustomer (s/create-lookup-writer-handler h/invite-customer l/invite-customer-lookup nil)}


