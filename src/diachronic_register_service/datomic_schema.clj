(ns diachronic-register-service.datomic-schema
  (:require [datomic.api :as d]
            [diachronic-register-service.datomic-schema-utils :refer :all]))

(def schema
  [;; TODO there is a future corpus/subcorpus distinction we want to make
   ;; (-> (attribute :document) type-ref cardinality-one component (docstring "document component"))
   (-> (attribute :document/subcorpus) type-string indexed cardinality-one)
   (-> (attribute :document/corpus)    type-string indexed cardinality-one)
   ;; (-> (attribute :document/metadata)  type-ref    cardinality-many component) ;; Not used??
   (-> (attribute :document/category)  type-string indexed cardinality-many)
   (-> (attribute :document/basename)  type-string cardinality-one) ;; Not always unique (e.g. The Sun)
   (-> (attribute :document/title)     type-string cardinality-one)
   (-> (attribute :document/gender)    type-ref    indexed cardinality-one (docstring "document author's gender"))
   (-> (attribute :document.gender/female) type-keyword cardinality-one) ;; Fails??
   (-> (attribute :document.gender/male)   type-keyword cardinality-one)
   (-> (attribute :document.gender/mixed)  type-keyword cardinality-one)
   ;; {:db/id #db/id[:db.part/db] :db/ident :document.gender/female}
   ;; {:db/id #db/id[:db.part/db] :db/ident :document.gender/male}
   ;; {:db/id #db/id[:db.part/db] :db/ident :document.gender/mixed}
   (-> (attribute :document/author-year) type-long indexed cardinality-one (docstring "document author birth decade"))
   (-> (attribute :document/year) type-long indexed cardinality-one (docstring "document year published"))
   (-> (attribute :document/audience)    type-string indexed cardinality-one (docstring "c-code audience"))
   (-> (attribute :document/media)       type-string indexed cardinality-one (docstring "c-code media"))
   (-> (attribute :document/topic)       type-string indexed cardinality-one (docstring "c-code topic"))

   ;; FIXME: how do we get DP?
   ;; (-> (attribute :document/morphemes))

   ;; TODO The Sun corpus specific metadata

   (make-boolean-schema :document :forward-or-afterward)
   (make-boolean-schema :document :dialog)
   (make-boolean-schema :document :quotation-type)
   (make-boolean-schema :document :visual)
   (make-boolean-schema :document :db-or-list)
   (make-boolean-schema :document :archaic)
   (make-boolean-schema :document :foreign-language)
   (make-boolean-schema :document :math-or-code)
   (make-boolean-schema :document :legalese)
   (make-boolean-schema :document :questionable-content)
   (make-boolean-schema :document :low-content)
   (make-boolean-schema :document :protagonist-personal-pronoun)
   (make-ordinal-schema :document :target-audience)
   (make-ordinal-schema :document :hard-soft)
   (make-ordinal-schema :document :informality)
   (make-ordinal-schema :document :addressing)
   (make-ordinal-schema :document :other-addressing)


   (-> (attribute :document/length) type-long indexed cardinality-one)
   (-> (attribute :document/paragraphs) type-ref cardinality-many component indexed)
   ;;(-> (attribute :paragraph) type-ref cardinality-many indexed component (docstring "paragraph comprised of tags and sentences in document"))
   ;;(-> (attribute :paragraph/document) type-ref cardinality-one)
   (-> (attribute :paragraph/tags) type-keyword indexed cardinality-many)
   (-> (attribute :paragraph/sentences) type-ref indexed cardinality-many component)
   (-> (attribute :sentence/text) type-string cardinality-one #_fulltext no-history)

   ;; Morpheme (maybe LUW/tree also?) data
   ;; LUW-type data is hard to extract from non-contemporary Japanese
   ;; (-> (attribute :complex-word/lemma) type-string cardinality-many)
   ;; (-> (attribute :complex-word/orth-base) type-string cardinality-many)
   ;; (-> (attribute :complex-word/pos) type-string cardinality-many)
   ;; Try to move most metadata to sentence level to reduce search time?!
   (-> (attribute :sentence/words) type-ref cardinality-many indexed component)
   (-> (attribute :word/position) type-long cardinality-one)
   (-> (attribute :word/lemma) type-string indexed cardinality-one)
   (-> (attribute :word/pos) type-string cardinality-one)
   (-> (attribute :word/orth-base) type-string cardinality-one)

   ;; Need: a way of getting a sequence of morphemes/sentences/paragraphs with tag information.
   ;;(-> (attribute :lemma/stats))
   ])
