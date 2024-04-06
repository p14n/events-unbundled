(ns common.base.core
  #?(:org.babashka/nbb (:require ["uuid" :refer [v4]]))
  #?(:clj (:import [java.util UUID])))


(defn first-of-type [type events]
  (some->> events
           (filter #(= (:type %) type))
           (first)))

(defn uuid []
  #?(:org.babashka/nbb (v4)
     :clj (str (UUID/randomUUID))))