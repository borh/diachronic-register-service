(ns diachronic-register-service.datomic-schema-utils
  (:require [datomic.api :as d]))

;; https://gist.github.com/a2ndrade/5651419
;; https://github.com/friemen/cugb/blob/master/datomic/src/schema.clj

(defn attribute [ident]
  {:db.install/_attribute :db.part/db
   :db/id (d/tempid :db.part/db)
   :db/ident ident
   ;; Default docstring:
   :db/doc (clojure.string/join " " (-> ident str (clojure.string/split #"/")))})

(defn docstring [m docstring]
  (assoc m :db/doc docstring))

(defn cardinality-one [m]
  (assoc m :db/cardinality :db.cardinality/one))

(defn cardinality-many [m]
  (assoc m :db/cardinality :db.cardinality/many))

(defn type-string [m]
  (assoc m :db/valueType :db.type/string))

(defn type-long [m]
  (assoc m :db/valueType :db.type/long))

(defn type-boolean [m]
  (assoc m :db/valueType :db.type/boolean))

(defn type-keyword [m]
  (assoc m :db/valueType :db.type/keyword))

(defn type-ref [m]
  (assoc m :db/valueType :db.type/ref))

(defn component [m]
  (assoc m :db/isComponent true))

(defn unique [m]
  (assoc m :db/unique :db.unique/value))

(defn no-history [m]
  (assoc m :db/noHistory true))

(defn fulltext [m]
  (assoc m :db/fulltext true))

(defn indexed [m]
  (assoc m :db/index true))

(defn make-ordinal-schema [ns-name ident-name]
  (-> (attribute (keyword (str (name ns-name) "/" (name ident-name))))
      (docstring (str (name ident-name) " in " (name ns-name) " (ordinal)"))
      type-long
      cardinality-one))

(defn make-boolean-schema [ns-name ident-name]
  (-> (attribute (keyword (str (name ns-name) "/" (name ident-name))))
      (docstring (str (name ident-name) " in " (name ns-name) " (boolean)"))
      type-boolean
      cardinality-one))
