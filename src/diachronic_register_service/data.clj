(ns diachronic-register-service.data
  (:require [schema.core :as s]
            [plumbing.core :refer [for-map map-keys map-vals ?> ?>> update-in-when]]
            [taoensso.timbre :as log]

            [datomic.api :as d]

            [clojure.core.reducers :as r]
            [clojure.set :as set]
            [clojure.string :as str]

            [loom.graph :as g]
            [loom.alg :as a]

            [clj-mecab.parse :as parse]
            [corpus-utils.bccwj :as bccwj]
            [corpus-utils.kokken :as kokken]
            [corpus-utils.text :as text]
            [corpus-utils.document :refer [SentencesSchema]]
            [d3-compat-tree.tree :as tree :refer [IndexedTree]]

            [diachronic-register-service.schemas :refer [CorpusOptions StatMap Metadata->StatMap Document Facet StringNumberMap MetadataMap]]
            [diachronic-register-service.stats :as stats])
  (:import [clojure.lang PersistentHashSet]
           [datomic.peer Connection]))

(s/defn document-to-datoms :- [{s/Keyword s/Any}]
  [paragraphs :- SentencesSchema
   metadata   :- {s/Keyword s/Any}
   options    :- CorpusOptions
   dic-type   :- s/Keyword]
  (let [document-id {:db/id #db/id[:db.part/user]}
        document-e (merge document-id
                          (-> (->> (select-keys metadata (:metadata-keys options))
                                   (remove (fn [[k v]]
                                             (or (nil? v) (and (not (or (set? v) (number? v) (keyword? v))) (empty? v)))))
                                   (into {})
                                   (map-keys #(keyword "document" (name %))))
                              (update-in-when [:document/category]
                                              (fn [categories]
                                                (let [category-1 (nth categories 0 nil)
                                                      category-2 (nth categories 1 nil)
                                                      category-3 (nth categories 2 nil)
                                                      category-4 (nth categories 3 nil)
                                                      tree
                                                      (-> {}
                                                          (?> category-1 (assoc :category/name category-1))
                                                          (?> category-2 (assoc-in [:category/child] {:category/name category-2}))
                                                          (?> category-3 (assoc-in [:category/child :category/child] {:category/name category-3}))
                                                          (?> category-4 (assoc-in [:category/child :category/child :category/child] {:category/name category-4})))]
                                                  (log/info "Tree" tree "Categories" category-1 category-2 category-3 category-4)
                                                  tree)))
                              (update-in-when [:document/gender]
                                              (fn [gender] (keyword "document.gender" (name gender))))))

        paragraphs-e (for [{:keys [tags sentences]} paragraphs]
                       ;;e
                       ;;{:db/id #db/id[:db.part/user] :paragraph/document document-id}
                       (let [sentences-e
                             (map
                              (fn [sentence]
                                {:sentence/text sentence
                                 :sentence/words (parse/with-dictionary dic-type
                                                   (->> sentence
                                                        parse/parse-sentence
                                                        (map-indexed
                                                         (fn [position word]
                                                           {:word/position position
                                                            :word/lemma (:lemma word)
                                                            :word/pos (:pos-1 word)
                                                            :word/orth-base (:orth-base word)}))))})
                              sentences)]
                         (if (empty? tags)
                           {:paragraph/sentences sentences-e}
                           {:paragraph/sentences sentences-e :paragraph/tags tags})))]
    ;;(into [document-e] paragraphs-e)
    [(assoc document-e :document/paragraphs paragraphs-e :document/length (count (mapcat :sentence/words paragraphs-e)))]))

(s/defn load-taiyo-data
  [connection :- Connection
   options :- CorpusOptions]
  (log/info ";; Loading Taiyo corpus" connection options)
  (doseq [{:keys [metadata paragraphs]}
          (take 100 (kokken/document-seq (:corpus-dir options)))]
    (log/info metadata (count paragraphs))
    @(d/transact-async connection ;; FIXME is this the right transaction granularity? try 1000
                       (document-to-datoms paragraphs metadata options :unidic-MLJ))))

(s/defn load-bccwj-data
  [connection :- Connection
   options :- CorpusOptions]
  (log/info ";; Loading BCCWJ corpus" connection options)
  (doseq [{:keys [metadata paragraphs]} ;; FIXME make non-BCCWJ specific
          (take 100 (bccwj/document-seq (:metadata-dir options) (:corpus-dir options)))]
    (log/info metadata (count paragraphs))
    @(d/transact-async connection ;; FIXME is this the right transaction granularity?
                       (document-to-datoms paragraphs (update-in metadata [:category] #(->> % next (into []))) options :unidic))))

(s/defn load-data
  [connection :- Connection
   options :- {s/Keyword CorpusOptions}]
  (load-taiyo-data connection (-> options :taiyo))
  (load-bccwj-data connection (-> options :bccwj)))

;; TODO Think about the structure of the data we return here. Should it be a zipper tree, should different attributes have different types of values (spans for years, and/*or* support for nominal, arbitrary functions (not as EDN, though) as subsets of selection, etc. as maybe types/records/protocols) in the tree?
;; How do we encode dependencies between different nodes/levels in the tree? Does it even need to be a tree--why not just a hashmap (cf. limits of nesting in update-in)? Every query on the database should then return a 'possible valid subset' of the metadata to pick from in the front-end? Can these dependencies between attributes be encoded in an index step? How to visualize the different paths possible within the metadata hierarchy tree (i.e. how to show interdependencies between selectables where selecting one box activates or closes off access to another)? Even if there are infinite (or close to infinite) possible paths, is it possible to calculate them on the fly in response to user input?

(defn prettify-name [s]
  (-> (if (or (string? s) (keyword? s)) s (str s))
      name
      (str/replace "-" " ")))

(defn deep-merge-with [f & maps]
  (apply
    (fn m [& maps]
      (if (every? map? maps)
        (apply merge-with m maps)
        (apply f maps)))
    maps))

(defn category-map->vector [r m]
  (if-let [category (:category/name m)]
    (if-let [child (:category/child m)]
      (recur (conj r category) child)
      r)
    r))

(s/defn transform-metadata :- (s/either [(s/either s/Str s/Keyword s/Num)]
                                        #{(s/either s/Str s/Keyword s/Num)}
                                        IndexedTree)
  [connection :- Connection
   [k v] :- [(s/one s/Keyword "k") s/Any]]
  (case k

    :document/gender (->> v
                          (sequence (map (fn [e] (:db/ident (d/entity (d/db connection) e)))))
                          frequencies
                          (map (fn [[k freq]] {k {:name (prettify-name k) :count freq :checked false}}))
                          (apply merge)
                          #_(into []))

    :document/category
    (tree/seq-to-indexed-tree
     (->> v
          (map
           (fn [e]
             (->> (if (instance? datomic.query.EntityMap e)
                    e
                    (d/entity (d/db connection) e))
                  d/touch
                  (category-map->vector []))))
          (filter seq)
          (map (fn [cs] {:genre cs :checked false :count 1})))
     {:root-name "Categories" :merge-fns {:count + :checked (fn [& xs] (or (first xs) false))}})

    ;; Else:
    (->> v
         frequencies
         (map (fn [[k freq]] {k {:name (prettify-name k) :count freq :checked false}}))
         (apply merge))
    #_(into [] (sort v))))

(def metadata-records ;; TODO ordering should reflect optimal query pattern
  [:document/subcorpus
   :document/corpus
   :document/category
   :document/gender
   :document/author-year
   :document/year
   :document/audience
   :document/media
   :document/topic

   ;; BCCWJ-specific metadata
   :document/forward-or-afterward
   :document/dialog
   :document/quotation-type
   :document/visual
   :document/db-or-list
   :document/archaic
   :document/foreign-language
   :document/math-or-code
   :document/legalese
   :document/questionable-content
   :document/low-content
   :document/protagonist-personal-pronoun
   :document/target-audience
   :document/hard-soft
   :document/informality
   :document/addressing
   :document/other-addressing

   :paragraph/tags])

(s/defn get-all-metadata :- {s/Keyword (s/either #{(s/either s/Str s/Keyword s/Num)}
                                                 [(s/either s/Str s/Keyword s/Num)]
                                                 IndexedTree)}
  [connection :- Connection]
  (for-map [[k v]
            (->>
             (d/query
              {:query '[:find ;;?attr-name ?attr-value
                        ?attr-name ?attr-value
                        :in $ [?chosen-attr ...]
                        :where
                        [?ref ?chosen-attr ?attr-value]
                        [?chosen-attr :db/ident ?attr-name]]
               :args [(d/db connection)
                      metadata-records]
               :timeout 10000})
             (r/fold
              (r/monoid
               (fn [a [k v]]
                 ;;(println a k v)
                 (update-in a [k] (fnil conj []) v))
               (fn [] {})))
             #_(r/fold
                (r/monoid
                 (fn [a k v]
                   (println k v)
                   #_(assoc a k (transform-metadata connection k v)))
                 (fn [] {}))))]
      k (transform-metadata connection [k v])))

(s/defn categorize-rules :- {s/Keyword {s/Keyword [Facet]}}
  [rules :- [Facet]]
  (->> rules
       (group-by (comp keyword namespace ffirst))
       (map-vals (partial group-by (comp keyword name ffirst)))))

(s/defn unroll-rules :- {s/Keyword [Facet]}
  [rules :- {s/Keyword {s/Keyword [Facet]}}]
  (println rules)
  (for-map [metadata-first-level [:document :paragraph :word]
            :when (get rules metadata-first-level nil)]
      metadata-first-level
    (for [metadata-second-level (map (comp keyword name) (conj metadata-records :word/orth-base :word/lemma :word/pos))
          :let [facets (get-in rules [metadata-first-level metadata-second-level] nil)]
          :when facets]
      {(keyword (name metadata-first-level) (name metadata-second-level))
       (into #{}
             (flatten
              (for [facet facets]
                (vals facet))))})))

;; d/filter all we can before using datalog?

(s/defn filter-with-rule
  [db
   rule :- {s/Keyword #{s/Any}}
   e-or-v :- (s/enum :e :v)
   datoms :- [java.lang.Object]]
  (log/info "Filtering... " e-or-v "rule" rule "datoms" datoms)
  (let [[rule-k rule-vs] (first rule)]
    ;;(println "rule values:" rule-vs "rule-k" rule-k "datoms" datoms)
    (->> datoms
         (r/filter
          (fn [e]
            (let [v (rule-k (d/entity db (e-or-v e)))]
              (if (set? v)
                (set/intersection v rule-vs)
                (rule-vs v))))))))

(s/defn filter-with-rules
  [db
   rules :- [{s/Keyword #{s/Any}}]
   e-or-v :- (s/enum :e :v)
   datoms :- [java.lang.Object]]
  ;; FIXME this implements OR search, while we really want OR+AND+NOT search (or both?)!
  (log/info "Filtering... " e-or-v "rules" rules "datoms" datoms)
  (r/reduce
   (r/monoid
    (fn [es rule]
      (filter-with-rule db rule e-or-v es))
    (fn [] datoms))
   rules))

(s/defn initial-scan :- [java.lang.Object]
  [db
   e-or-v
   facet :- {s/Keyword #{s/Any}}]
  (let [[k vs] (first facet)
        first-v (first vs)
        rest-v (next vs)]
    (log/info "Performing initial scan on" "k" k "first" first-v "rest" rest-v "using" e-or-v)
    (seq (if rest-v
           (filter-with-rule db facet e-or-v (d/datoms db :avet k))
           (d/datoms db :avet k first-v)))))

(s/defn get-metadata-statistics :- (s/maybe {s/Keyword (s/either IndexedTree {s/Any s/Any})})
  "Returns the metadata frequency distribution of given facet."
  [connection :- Connection
   facets :- [Facet]]
  (let [db (d/db connection)
        facets (-> facets categorize-rules unroll-rules)
        first-rule (-> facets :document first) ;; :document/* only
        rest-rules (-> facets :document rest (into []))]
    (log/info {:facets facets :first-rule first-rule :rest-rules rest-rules})
    (->> first-rule ;; The first rule to search the index with. Should be a good discriminator for the final result.
         (initial-scan db :e)
         (?>> (not-empty rest-rules) (filter-with-rules db rest-rules :e))
         (r/map (fn [{:keys [e]}] (d/entity db e)))
         (r/fold
          (r/monoid
           (fn [m e]
             ;;(clojure.pprint/pprint {:m m})
             ;;(clojure.pprint/pprint {:e e})
             (for-map [metadata metadata-records
                       :let [extracted-metadata (metadata e)]
                       :when (or (metadata m) extracted-metadata)]
                 metadata (if extracted-metadata (conj (metadata m) extracted-metadata) (metadata m))))
           (fn [] {})))
         (r/reduce
          (fn [m k es]
            ;;(println "k es" k es)
            (assoc m k (transform-metadata connection [k es])))
          {})
         (map-vals
          (fn [v]
            (if (map? v)
              v
              (apply merge (map (fn [[k freq]] {k {:name (prettify-name k) :count freq}}) (frequencies v)))
              #_(map-vals (fn [freq] {:name v :count freq})
                        (frequencies v))))))))

(comment
  (get-metadata-statistics (-> reloaded.repl/system :db :connection) [{:document/subcorpus "OM"}]))

(comment
  (defn rewrite-children [r m]
    (if-let [category-name (:name m)]
      (if-let [children (:children m)]
        (let [children-map (for-map [child children]
                               (:name child) child)]
          (recur r m)))))
  (defn test-tree []
    (tree/seq-to-tree
     [{:genre ["a" "b" "c" "d"] :count 1 :checked false}
      {:genre ["a" "b" "e" "f"] :count 1 :checked false}
      {:genre ["k" "l"] :count 1 :checked false}
      {:genre ["a" "m" "o" "p"] :count 1 :checked false}]
     {:root-name "Categories" :merge-fns {:count + :checked (fn [a b] a)}})
    #_(clojure.walk/postwalk
       (fn [x]
         ;;(println x)
         (if (= :children (first x))
           (do ;;(println x)
             (update-in x [1] (for-map [child (nth x 1)] (do (println child) (:name child)) child)))
           x))
       ;;identity
       )))

(s/defn get-all-metadata-pull :- {s/Keyword #{s/Any}}
  [connection :- Connection]
  (d/pull (d/db connection) metadata-records))

(comment
  (require '[criterium.core :as c])
  (c/bench (get-all-metadata (-> reloaded.repl/system :db :connection)))
  )

(s/defn extract-collocations
  [n :- s/Num
   coll :- [s/Str]]
  (when (>= (count coll) n)
    ;;(log/info (seq coll))
    (apply vector (subvec coll 0 n) (extract-collocations n (subvec (vec coll) 1)))))

(s/defn index-collocations
  "Should generate collocations matching specified relation and commit them to the database."
  [connection :- Connection
   relation-fn :- clojure.lang.IFn]
  (d/q '{:find [(diachronic-register-service.data/extract-collocations 2 ?lemma)] ; <- relation-fn
         :with [?sentence]
         :where [;;[?paragraph :paragraph/document ?document]
                 ;;[?paragraph :paragraph/sentences ?sentence]
                 [?sentence :sentence/words ?word]
                 [?word :word/lemma ?lemma]]}
       (d/db connection)))

(s/defn index-counts
  "Should pre-compute needed meta-information for arbitrary language features."
  [connection :- Connection]
  (d/q '{:find [?]}
       (d/db connection)))

(s/defn make-dynamic-query :- [[s/Any]]
  [facets :- {s/Keyword s/Any #_(s/enum [(s/enum s/Str s/Num)] s/Str s/Num)}]
  (into []
   (for [[k v] facets]
     (let [?q (symbol (str "?" (namespace k)))]
       [?q k v]))))

(comment
  (s/defn get-morpheme-graph-2 :- (s/maybe {s/Str s/Num})
    "Returns collocations of given morpheme occurring in indicated metadata."
    [connection :- Connection
     lemma :- s/Str
     facets :- [Facet]]
    (let [query (->
                 '{:find [(clojure.core/frequencies ?clemma)]
                   :with [?cword]
                   :in [$ ?lemma]
                   :where [[?word :word/lemma ?lemma]
                           [?sentence :sentence/words ?word]
                           [?sentence :sentence/words ?cword]
                           [?cword :word/lemma ?clemma]
                           [(!= ?lemma ?clemma)]

                           ;;[?document :document/paragraphs ?paragraph]
                           ;;[?paragraph :paragraph/sentences ?sentence]
                           ]}

                 ;;(?> facets (update-in [:where] conj '[?e ?a ?v]))
                 ;;(?> facets (update-in [:in] conj '[[?e ?a ?v] ...]))
                 (?> facets (update-in [:where] (fn [w] (into (make-dynamic-query facets) w))))
                 #_(doto log/info))]
      (->> (d/q query (d/db connection) lemma)
           ffirst))))

(s/defn get-morpheme-graph-2 :- (s/maybe {s/Str s/Num})
  "Returns the frequency distribution of given facet. Specifying a lemma will return that lemma's frequency, otherwise it returns all lemma within the facet."
  [connection :- Connection
   facets :- [Facet]]
  (let [db (d/db connection)
        facets-by-ns (->> facets categorize-rules unroll-rules)

        transition-fn
        (fn [f-ns datoms]
          (log/info "Transitioning" f-ns)
          (case f-ns
            :document
            (r/mapcat (fn [{:keys [e]}] (d/datoms db :eavt e :document/paragraphs)) datoms)

            :paragraph
            (->> datoms
                 (r/mapcat (fn [{:keys [v]}] (d/datoms db :eavt v :paragraph/sentences)))
                 (r/mapcat (fn [{:keys [v]}] (d/datoms db :eavt v :sentence/words))))

            datoms))]
    (clojure.pprint/pprint {:unrolled facets-by-ns})
    ;;(log/info "Facets: " facets "\n" first-rule)
    ;; FIXME intelligently skip over document/paragraph traversal depending on given facets.
    ;; FIXME generate and use metadata statistics for optimal discrimination.

    #_{:document  [{s/Keyword #{(s/either s/Str s/Keyword s/Num)}}]
       :paragraph [{s/Keyword #{(s/either s/Str s/Keyword s/Num)}}]
       :word      [{s/Keyword #{(s/either s/Str s/Keyword s/Num)}}]}

    (->>
     (for [facet-ns [:document :paragraph :word]
           :let [facets (facet-ns facets-by-ns)]]
       [facet-ns (if facets facets :skip)])

     (r/reduce
      (r/monoid
       (fn [datoms [facet-ns facets]]
         ;;(println "<<<<<<<" datoms facet-ns facets)
         (if (= facets :skip)
           (do (println "Skipping....")
               (transition-fn facet-ns datoms))
           (let [[facet-k facet-v] (ffirst facets)
                 e-or-v (case facet-ns :document :e :paragraph :v :word :v)

                 datoms ;; Filtered datoms:
                 (if datoms
                   (filter-with-rules db facets e-or-v datoms)
                   ;; First rule special case:
                   (let [facets (next facets)
                         datoms (initial-scan db :e {facet-k facet-v})]
                     (if facets
                       (filter-with-rules db facets e-or-v datoms)
                       datoms)))]
             (clojure.pprint/pprint {:facet-ns facet-ns :e-or-v e-or-v :datoms datoms :facet-k facet-k :facet-v facet-v})
             ;;(clojure.pprint/pprint {:datom-1 (try (keys (d/entity db (.e (first (take 1 datoms))))) (catch Exception e))})
             ;; Mapping transitions between metadata levels:
             (transition-fn facet-ns datoms))))
       (fn [] nil)))

     (r/map (fn [{:keys [v]}] (:word/orth-base (d/entity db v))))
     (into [])
     frequencies)
    #_(->> first-rule ;; The first rule to search the index with. Should be a good discriminator for the final result.
         (apply d/datoms db :avet)
         (?>> (not-empty rest-rules) (filter-with-rules db rest-rules :e))
         ;; Mapping from documents to words.
         (r/mapcat (fn [{:keys [e]}] (d/datoms db :eavt e :document/paragraphs)))
         ;; Get the values from :eavt indexes!
         (?>> (:paragraph facets) (filter-with-rules db (:paragraph facets) :v))
         (r/mapcat (fn [{:keys [v]}] (d/datoms db :eavt v :paragraph/sentences)))
         (r/mapcat (fn [{:keys [v]}] (d/datoms db :eavt v :sentence/words)))
         (?>> (:word facets) (filter-with-rules db (:word facets) :v))
         (r/map (fn [{:keys [v]}] (:word/orth-base (d/entity db v))))
         (into [])
         frequencies)))

;; FIXME any way of making this a middleware? and working on d/datoms API?
(s/defn get-morpheme-graph-timeout
  [connection]
  (let [query []
        args []]
    (d/query {:query query :args args :timeout 10000})))

(comment
  (s/with-fn-validation (get-morpheme-graph-2 (-> reloaded.repl/system :db :connection) [{:word/orth-base "言う"} {:document/corpus "BCCWJ"} {:document/subcorpus "PM"}])))

;; Search strategy for comparing a word's cooccurrence distribution between two facets:
;; 1.  Prepare two datastructures (String->Num maps) to hold the data for both facets. Start at the word level, iterate through all the sentences, and based on their metadata, add to relevant facet datastructure.

(s/defn get-graphs :- {s/Keyword
                       {:graph        {s/Str s/Num}
                        :unique-words {s/Str s/Num}
                        :unique-prop  Double}
                       :common-words [stats/MorphemeStats]
                       :common-prop  Double}
  [connection :- Connection
   facets-map :- {s/Keyword [Facet]}]
  (let [data (for-map [[id facets] facets-map]
                 id {:graph (get-morpheme-graph-2 connection facets)})]
    (merge-with merge data (stats/generic-diff data))))

(comment
  (sm/defschema TokenStats
    "Either word or collocation. Or rather word, with collocation graph included in map?"
    {s/Keyword {s/Any s/Any}}))

(s/defn get-morpheme-graph-3 :- (s/maybe {Facet s/Num})
  "Returns the metadata frequency distribution of given morpheme. This is the counterpart of get-morpheme-graph-2."
  [connection :- Connection
   facets :- (s/maybe {s/Keyword s/Any #_(s/enum [(s/enum s/Str s/Num)] s/Str s/Num)})]
  (let [db (d/db connection)
        facets (categorize-rules facets)
        first-rule (ffirst (:word facets)) ;; :word/* only
        rest-rules (into [] (rest (:document facets)))]
    (log/info first-rule)
    (->> first-rule
         (apply d/datoms db :avet)
         (?>> rest-rules (filter-with-rules db rest-rules :v))
         (r/mapcat (fn [{:keys [e]}] (d/datoms db :vaet e :sentence/words)))
         (r/mapcat (fn [{:keys [e]}] (d/datoms db :vaet e :paragraph/sentences)))
         (r/mapcat (fn [{:keys [e]}] (d/datoms db :vaet e :document/paragraphs)))
         (r/map (fn [{:keys [e]}] (select-keys (d/entity db e) [:document/target-audience :document/author-year])))
         (frequencies))))

(s/defn get-morpheme-graph :- (s/maybe {s/Str s/Num})
  "Returns collocations of given morpheme occurring in indicated metadata."
  [connection :- Connection
   lemma :- s/Str
   facets :- (s/maybe {s/Keyword s/Any})]
  (let [query (->
               '{:find [(clojure.core/frequencies ?clemma)]
                 :with [?cword]
                 :in [$ ?lemma]
                 :where [[?word :word/lemma ?lemma]
                         [?sentence :sentence/words ?word]
                         [?sentence :sentence/words ?cword]
                         [?cword :word/lemma ?clemma]
                         [(!= ?lemma ?clemma)]

                         ;;[?document :document/paragraphs ?paragraph]
                         ;;[?paragraph :paragraph/sentences ?sentence]
                         ]}

               ;;(?> facets (update-in [:where] conj '[?e ?a ?v]))
               ;;(?> facets (update-in [:in] conj '[[?e ?a ?v] ...]))
               (?> facets (update-in [:where] (fn [w] (into (make-dynamic-query facets) w))))
               #_(doto log/info))]
    (->> (d/q query (d/db connection) lemma)
         ffirst)))

(comment
  (time (s/with-fn-validation (get-morpheme-graph (-> reloaded.repl/system :db :connection) "a" nil)))

  (time (d/q '{:find [(clojure.core/frequencies ?clemma)], :with [?cword], :in [$ ?lemma], :where [[?word :word/lemma ?lemma] [?sentence :sentence/words ?word] [?sentence :sentence/words ?cword] [?cword :word/lemma ?clemma] [(!= ?lemma ?clemma)]]}
             (d/db (-> reloaded.repl/system :db :connection))
             "事"))
  ;; "Elapsed time: 804.504046 msecs" -> 446


  (bench (dorun (d/q '{:find [(clojure.core/frequencies ?clemma)], :with [?cword], :in [$ ?lemma], :where [[?document :document/paragraphs ?paragraph]
                       [?paragraph :paragraph/sentences ?sentence] [?word :word/lemma ?lemma] [?sentence :sentence/words ?word] [?sentence :sentence/words ?cword] [?cword :word/lemma ?clemma] [(!= ?lemma ?clemma)]]}
                     (d/db (-> reloaded.repl/system :db :connection))
                     "花")))

  ;; Execution time mean : 3.472424 sec
  ;; Execution time std-deviation : 519.436798 ms
  ;; FIXME what was I trying to do here: too slow? Actually OOMs now... (need 4g)

)

(s/defn get-morpheme-strings :- [{:lemma s/Str s/Keyword s/Num}]
  "Given a datomic connection and a vector of queries, returns the morpheme frequency profile given the query constraints.
  Queries that specify more than one possible value must do so with a vector. TODO functions (>, <, ... + arbitrary functions); make queries ordered so we can take advantage of faster explicit queries in datalog."
  [connection :- Connection
   queries :- {s/Keyword s/Any #_(s/enum s/Str s/Num [s/Any] clojure.lang.IFn)}]
  (let [common-query '{:find [?lemma
                              (count ?lemma)
                              (count-distinct ?sentence)
                              (count-distinct ?paragraph)
                              (count-distinct ?document)]
                       :with [?word] ;; Aggregate at the word level
                       :in [$]}
        dynamic-query (apply merge-with into
                             (for [[k v] queries]
                               (let [?q (symbol (str "?" (namespace k)))]
                                 (if (vector? v) ;; TODO generalize to sequence
                                   (let [?qs (symbol (str "?" (name k)))]
                                     {:where [[?q k ?qs]] :in [[?qs '...]] :inputs [v]})
                                   {:where [[?q k v]]}))))
        where-query (update-in dynamic-query
                               [:where]
                               into '[[?document :document/paragraphs ?paragraph]
                                      [?paragraph :paragraph/sentences ?sentence]
                                      [?sentence :sentence/words ?word]
                                      [?word :word/lemma ?lemma]
                                      #_[?word :word/pos ?pos]])
        inputs (into [(d/db connection)] (:inputs dynamic-query))
        query (dissoc (merge-with into common-query where-query) :inputs)]
    (->> (apply d/q query inputs)
         (r/map (partial zipmap [:lemma :count :sentence-count :paragraph-count :document-count]))
         (r/map (fn [m] (assoc m :tf-idf (stats/tf-idf :rsj 10000000 (:count m) (:sentence-count m)))))
         (into [])
         #_(map first)
         #_frequencies)))

(comment
  (time (s/with-fn-validation (get-morpheme-strings (-> reloaded.repl/system :db :connection) {:document/corpus "BCCWJ"})))
  (time (->> (s/with-fn-validation (get-morpheme-strings (-> reloaded.repl/system :db :connection) {:document/corpus "BCCWJ"})) (sort-by :tf-idf >) (take 10)))
  (time (get-morpheme-strings (-> reloaded.repl/system :db :connection) {:document/subcorpus ["OC" "OM"] :document/corpus "Sun"})) ;; => []
  )


(s/defschema Morpheme
  {:word/orth-base s/Str
   :word/lemma s/Str
   :word/pos s/Str})
(s/defn get-morpheme-variants :- {Morpheme s/Num}
  "Return all variants of given morpheme, searched using its orth-base form."
  ;; FIXME profile: this should be blazing fast!
  [connection :- Connection
   orth-base :- s/Str]
  (->>
   (d/q '{:find [(pull ?word pattern)]
          :in [$ ?ob pattern]
          :where [[?word :word/orth-base ?orth-base]
                  [(= ?orth-base ?ob)]]}
        (d/db connection)
        orth-base
        [:word/orth-base :word/lemma :word/pos])
   (mapcat identity)
   frequencies))

(comment
  (time (s/with-fn-validation (get-morpheme-variants (-> reloaded.repl/system :db :connection) "こと"))))

;; TODO: sentence search based on word ent
(s/defn get-morpheme-sentences :- #{s/Str}
  "Return all variants of given morpheme, searched using its orth-base form."
  ;; FIXME profile: this should be blazing fast!
  [connection :- Connection
   orth-base :- s/Str
   limit :- s/Num]
  (->>
   (d/q '{:find [?text]
          :in [$ ?ob]
          :where [[?word :word/orth-base ?orth-base]
                  [(= ?orth-base ?ob)]
                  [?sentence :sentence/words ?word]
                  [?sentence :sentence/text ?text]]}
        (d/db connection)
        orth-base)
   (mapcat identity)
   (into #{})))

(comment
  (time (s/with-fn-validation (get-morpheme-sentences (-> reloaded.repl/system :db :connection) "こと" 5))))

(comment
  (clj-mecab.parse/with-dictionary-string :unidic (clj-mecab.parse/parse-sentence "今日は。"))
  (-> (d/entity (d/db (-> reloaded.repl/system :db :connection)) 17592186045420) (.get ":sentence/text"))
  (ns diachronic-register-service.data)
  (load-data (-> reloaded.repl/system :db :connection) {:metadata-dir "/data/BCCWJ-2012-dvd1/DOC/" :corpus-dir "/data/BCCWJ-2012-dvd1/C-XML/VARIABLE/" :metadata-keys #{:audience :media :topic :gender :category :addressing :target-audience :author-year :subcorpus :basename :title :year}})
  (d/q '[:find ?e ?v :where [?e :sentence/text ?v]] (d/db (-> reloaded.repl/system :db :connection))))
