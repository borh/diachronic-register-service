(ns diachronic-register-service.schemas
  (:require [schema.core :as s]
            [schema.coerce :as coerce]
            ;;[ring.swagger.schema :refer [defmodel]]

            [plumbing.core :refer [for-map]]
            [fast-zip.core]
            [corpus-utils.document :refer [MetadataSchema DocumentSchema]])
  (:import [clojure.lang PersistentQueue PersistentHashSet]
           [fast_zip.core ZipperLocation]))

(s/defschema CorpusOptions
  {(s/optional-key :metadata-dir) s/Str
   :corpus-dir s/Str
   :metadata-keys s/Any})

(s/defschema StringNumberMap
  "A map from strings to numbers."
  {s/Str s/Num})

(s/defschema MetadataMap
  "Modified MetadataSchema from corpus-utils that only contains optional keys, some of which can also be nil(?)."
  {(s/optional-key :gender)   (s/enum :male :female :mixed)
   (s/optional-key :audience) s/Str
   (s/optional-key :media)    s/Str
   (s/optional-key :topic)    s/Str
   (s/optional-key :target-audience)  (s/enum 1 2 3 4 5)
   (s/optional-key :hard-soft)        (s/enum 1 2 3 4)
   (s/optional-key :informality)      (s/enum 1 2 3)
   (s/optional-key :addressing)       (s/enum 1 2 3)
   (s/optional-key :other-addressing) (s/enum 1 2 3 4)
   :category [s/Str]}
  #_MetadataSchema)

(s/defschema StatMap
  {:df StringNumberMap
   :tf StringNumberMap
   (s/optional-key :tf-idf) StringNumberMap ;; FIXME
   (s/optional-key :document-count) s/Num
   (s/optional-key :documents) PersistentHashSet
   (s/optional-key :vocab) PersistentHashSet
   (s/optional-key :token-count) s/Num})

(s/defschema Metadata->StatMap
  {MetadataMap StatMap})

(s/defschema Document ;; FIXME: naming "Schema"
  (merge DocumentSchema
         {(s/optional-key :token-count) s/Num
          (s/optional-key :token-freqs) StringNumberMap}))

(s/defschema ModelSchema
  {;;:type (s/enum :global :subgroup)
   :stats StatMap
   :description s/Any})

(s/defschema MetadataPart
  {:name s/Keyword
   :value s/Any #_(s/enum s/Str [s/Str] #_{:tree ZipperLocation})
   :negated? Boolean})

(s/defschema MetadataPath
  [MetadataPart])

;; TODO use defmodel to specify API schemas.
(comment
  (defmodel APIQuery
    "API word query schema"
    {(s/optional-key :lemma) s/Str
     (s/optional-key :pos) s/Str
     :orth-base s/Str})

  (defmodel DBQuery
    "DB word query schema"
    {(s/optional-key :lemma) s/Str
     (s/optional-key :pos) s/Str
     :orth-base s/Str})

  (comment
    (def entry-coercer
      "A custom output coercer that is used to coerce a server-side Entry
   into a ClientEntry whenever it appears in a response"
      (fn [schema]
        (spit "test.log" schema)
        (when (= schema APIQuery)
          (fn [request x]
            (spit "x.log" x)
            ((coerce/coercer APIQuery coerce/json-coercion-matcher) x))))))

  (def entry-coercer
    "A custom output coercer that is used to coerce a server-side Entry
into a ClientEntry whenever it appears in a response"
    (fn [schema]
      (spit "test.log" schema)
      (when (= schema APIQuery)
        (fn [request x]
          (let [[first last] (clojure.string/split (:name x) #" ")]
            (-> x
                (dissoc :name)
                (assoc :first-name first
                       :last-name last))))))))


(comment
  ((coerce/coercer APIQuery coerce/json-coercion-matcher) {"orth-base" "audience"}))

(comment
  (defmodel Ack
    "Simple acknowledgement for successful requests"
    {:message (s/eq "OK")})

  (def ack
    {:status 200
     :body {:message "OK"}})

  (defmodel Missing
    {:message String})

  (s/defn not-found
    [message :- String]
    {:status 404
     :body {:message message}}))
