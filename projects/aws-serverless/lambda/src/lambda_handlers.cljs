(ns lambda-handlers
  (:require [handlers :as h]
            [projectors :as p]
            [lookups :as l]
            [writers :as w]
            [shell :as s]))


(clj->js {(s/handler-name-kw h/invite-customer) (s/create-lookup-writer-handler h/invite-customer l/invite-customer-lookup w/create-customer-email)
          (s/handler-name-kw p/project-customer) (s/create-lookup-writer-handler p/project-customer l/customer-lookup w/update-customer)})


