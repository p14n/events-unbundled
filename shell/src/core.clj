(ns core
  (:require [bff.core :as bff]
            [bff.cache :as cache]))


(defn start []
  (bff/start-bff cache/cache-handler))


; Get request
; Create ID
; Add ID and deferred to cache
; Send request to service
; Service updates kafka
; presentation builds model
; cache listens to kafka model stream
; cache populates deferred 

;data defsx
; bff route,method <- gql
;     forwarding action <- gql resolver
;     response action <-gql + ids
; processor 
;     kafka topic
;     event type
; presentation
;     kafka topic
;     event type