(ns diachronic-register-service.features
  (:require
   [schema.core :as s]
   [schema.macros :as sm]

   [datomic.api :as d]))

;; A context can be anything from a given corpus, time period, NDC number or gender marker.
(defn word-distribution
  "Get a word distribution from given context.
  Returns a map of words with their probabilities."
  [context])

(defn cooccurrence-distribution
  "Get a cooccurrence distribution from given context.
  Returns a graph, with weighted edges representing probabilities."
  [context])
