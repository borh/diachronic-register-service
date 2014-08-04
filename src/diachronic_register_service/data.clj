(ns diachronic-register-service.data
  (:require [schema.core :as s]
            [schema.macros :as sm]
            [plumbing.core :refer [map-keys ?> ?>> update-in-when]]

            [datomic.api :as d]

            [clojure.core.reducers :as r]
            [clojure.set :as set]

            [clj-mecab.parse :as parse]
            [corpus-utils.bccwj :as bccwj]
            [corpus-utils.kokken :as kokken]
            [corpus-utils.text :as text]
            [corpus-utils.document :refer [SentencesSchema]]

            [diachronic-register-service.schemas :refer [CorpusOptions StatMap Metadata->StatMap Document StringNumberMap MetadataMap]]
            [diachronic-register-service.stats :as stats])
  (:import [clojure.lang PersistentHashSet]
           [datomic.peer Connection]))

(sm/defn document-to-datoms :- [{s/Keyword s/Any}]
  [paragraphs :- SentencesSchema
   metadata :- {s/Keyword s/Any}
   options :- CorpusOptions
   dic-type :- s/Keyword]
  (let [document-id {:db/id #db/id[:db.part/user]}
        document-e (merge document-id
                          (-> (->> (select-keys metadata (:metadata-keys options))
                                   (remove (fn [[k v]]
                                             (or (nil? v) (and (not (or (set? v) (number? v) (keyword? v))) (empty? v)))))
                                   (into {})
                                   (map-keys #(keyword "document" (name %))))
                              ;;(update-in-when [:document/category] #(->> % next (clojure.string/join "\t")))
                              (update-in-when [:document/gender] #(keyword "document.gender" (name %)))))

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
    [(assoc document-e :document/paragraphs paragraphs-e :document/length (count (mapcat :sentence/words paragraphs-e)))]
    ))

(sm/defn load-taiyo-data
  [connection :- s/Any
   options :- CorpusOptions]
  (println ";; Loading Taiyo corpus" connection options)
  (doseq [{:keys [metadata paragraphs]}
          (take 100 (kokken/document-seq (:corpus-dir options)))]
    (println metadata (count paragraphs))
    @(d/transact-async connection ;; FIXME is this the right transaction granularity? try 1000
                       (document-to-datoms paragraphs metadata options :unidic-MLJ))))

(sm/defn load-bccwj-data
  [connection :- s/Any
   options :- CorpusOptions]
  (println ";; Loading BCCWJ corpus" connection options)
  (doseq [{:keys [metadata paragraphs]} ;; FIXME make non-BCCWJ specific
          (take 100 (bccwj/document-seq (:metadata-dir options) (:corpus-dir options)))]
    (println metadata (count paragraphs))
    @(d/transact-async connection ;; FIXME is this the right transaction granularity?
                       (document-to-datoms paragraphs (update-in metadata [:category] #(->> % next (into []))) options :unidic))))

(sm/defn load-data
  [connection :- s/Any
   options :- {s/Keyword CorpusOptions}]
  (load-taiyo-data connection (-> options :taiyo))
  (load-bccwj-data connection (-> options :bccwj)))

;; TODO Think about the structure of the data we return here. Should it be a zipper tree, should different attributes have different types of values (spans for years, and/*or* support for nominal, arbitrary functions (not as EDN, though) as subsets of selection, etc. as maybe types/records/protocols) in the tree?
;; How do we encode dependencies between different nodes/levels in the tree? Does it even need to be a tree--why not just a hashmap (cf. limits of nesting in update-in)? Every query on the database should then return a 'possible valid subset' of the metadata to pick from in the front-end? Can these dependencies between attributes be encoded in an index step? How to visualize the different paths possible within the metadata hierarchy tree (i.e. how to show interdependencies between selectables where selecting one box activates or closes off access to another)? Even if there are infinite (or close to infinite) possible paths, is it possible to calculate them on the fly in response to user input?

(sm/defn get-all-metadata
  [connection :- s/Any]
  (->>
   (d/q '[:find ?attr-name (distinct ?attr-value)
          :in $ [?chosen-attr ...]
          :where
          [?ref ?chosen-attr ?attr-value]
          [?chosen-attr :db/ident ?attr-name]]
        (d/db connection)
        [:document/subcorpus
         :document/corpus
         :document/category
         :document/gender
         :document/author-year
         :document/year
         :document/audience
         :document/media
         :document/topic
         :paragraph/tags])
   flatten
   (apply hash-map)))

(sm/defn extract-collocations
  [n :- s/Num
   coll :- [s/Str]]
  (when (>= (count coll) n)
    (apply vector (subvec coll 0 n) (extract-collocations n (subvec coll 1)))))

(sm/defn index-collocations
  "Should generate collocations matching specified relation and commit them to the database."
  [connection :- s/Any
   relation-fn :- clojure.lang.IFn]
  (d/q '{:find [(diachronic-register-service.data/extract-collocations 2 ?lemma)]
         :with [?sentence]
         :where [;;[?paragraph :paragraph/document ?document]
                 ;;[?paragraph :paragraph/sentences ?sentence]
                 [?sentence :sentence/words ?word]
                 [?word :word/lemma ?lemma]]}
       (d/db connection)))

(sm/defn index-counts
  "Should pre-compute needed meta-information for arbitrary language features."
  [connection :- s/Any]
  (d/q '{:find [?]}
       (d/db connection)))

(sm/defn make-dynamic-query :- [[s/Any]]
  [facets :- {s/Keyword s/Any #_(s/enum [(s/enum s/Str s/Num)] s/Str s/Num)}]
  (into []
   (for [[k v] facets]
     (let [?q (symbol (str "?" (namespace k)))]
       [?q k v]))))

;; d/filter all we can before using datalog?

(sm/defn filter-with-rules
  [db
   rules :- [{s/Keyword s/Any}]
   e-or-v :- (s/enum :e :v)
   ds]
  ;; FIXME this implements AND search, while we really want OR+AND+NOT search (or both?)!
  (println "Filtering... " e-or-v rules)
  (r/reduce
   (r/monoid
    (fn [es rule]
      (let [[rule-k rule-v] (first rule)]
        (r/filter (fn [e]
                    (let [v (rule-k (d/entity db (e-or-v e)))]
                      (if (set? v)
                        (contains? v rule-v)
                        (= rule-v v))))
                  es)))
    (fn [] ds))
   rules))

(sm/defn categorize-rules :- {s/Keyword [{s/Keyword s/Any}]}
  [rules :- [{s/Keyword s/Any}]]
  (->> rules
       (group-by (comp keyword namespace ffirst))))

(sm/defn get-morpheme-graph-2 :- (s/maybe {s/Str s/Num})
  [connection
   facets :- [{s/Keyword s/Any #_(s/enum [(s/enum s/Str s/Num)] s/Str s/Num)}]]
  (let [db (d/db connection)
        facets (categorize-rules facets)
        first-rule (ffirst (:document facets)) ;; :document/* only
        rest-rules (into [] (rest (:document facets)))]
    (println facets "\n" first-rule)
    (->> first-rule ;; The first rule to search the index with. Should be a good discriminator for the final result.
         (apply d/datoms db :avet)
         (?>> (not-empty rest-rules) (filter-with-rules db rest-rules :e))
         ;; Mapping from documents to words.
         (r/mapcat (fn [{:keys [e]}] (d/datoms db :eavt e :document/paragraphs)))
         ;; Get the values from :eavt indexes!
         (?>> (:paragraph facets) (filter-with-rules db (:paragraph facets) :v))
         (r/mapcat (fn [{:keys [v]}] (d/datoms db :eavt v :paragraph/sentences)))
         (r/mapcat (fn [{:keys [v]}] (d/datoms db :eavt v :sentence/words)))
         (?>> (:word facets) (filter-with-rules db (:word facets) :v))
         (r/map (fn [{:keys [v]}] (:word/lemma (d/entity db v))))
         (into [])
         frequencies)))

(sm/defn get-graphs :- {:data [{s/Str s/Num}]
                        :stats stats/GraphStats}
  [connection :- Connection
   facets-seq :- [[{s/Keyword s/Any #_(s/enum [(s/enum s/Str s/Num)] s/Str s/Num)}]]]
  (println connection (class connection) (type connection))
  (let [data (mapv (partial get-morpheme-graph-2 connection) facets-seq)]
    {:data data
     :stats (apply stats/generic-diff data)}))

;; WARNING: Final GC required 1.141904394207189 % of runtime
;; Evaluation count : 120 in 60 samples of 2 calls.
;;              Execution time mean : 616.107611 ms
;;     Execution time std-deviation : 91.223223 ms
;;    Execution time lower quantile : 477.221374 ms ( 2.5%)
;;    Execution time upper quantile : 752.767489 ms (97.5%)
;;                    Overhead used : 15.687377 ns

(comment
  (sm/defschema TokenStats
    "Either word or collocation. Or rather word, with collocation graph included in map?"
    {s/Keyword {s/Any s/Any}}))

(sm/defn get-morpheme-graph-3 ;;:- (s/maybe {s/Str s/Num})
  [connection
   facets :- (s/maybe {s/Keyword s/Any #_(s/enum [(s/enum s/Str s/Num)] s/Str s/Num)})]
  (let [db (d/db connection)
        first-rule (ffirst facets) ;; :word/* only
        rest-rules (into [] (rest facets))]
    (->> first-rule
         (apply d/datoms db :avet)
         (?>> rest-rules (filter-with-rules db rest-rules))
         (r/mapcat (fn [{:keys [e]}] (d/datoms db :vaet e :sentence/words)))
         (r/mapcat (fn [{:keys [e]}] (d/datoms db :vaet e :paragraph/sentences)))
         (r/mapcat (fn [{:keys [e]}] (d/datoms db :vaet e :document/paragraphs)))
         (r/map (fn [{:keys [e]}] (:document/title (d/entity db e))))
         (into []))))

(sm/defn get-morpheme-graph :- (s/maybe {s/Str s/Num})
  [connection
   lemma :- s/Str
   facets :- (s/maybe {s/Keyword s/Any #_(s/enum [(s/enum s/Str s/Num)] s/Str s/Num)})]
  (let [query (->
               '{:find [(clojure.core/frequencies ?clemma)]
                 :with [?cword]
                 :in [$ ?lemma]
                 :where [[?word :word/lemma ?lemma]
                         [?sentence :sentence/words ?word]
                         [?sentence :sentence/words ?cword]
                         [?cword :word/lemma ?clemma]
                         [(!= ?lemma ?clemma)]

                         [?document :document/paragraphs ?paragraph]
                         [?paragraph :paragraph/sentences ?sentence]]}

               ;;(?> facets (update-in [:where] conj '[?e ?a ?v]))
               ;;(?> facets (update-in [:in] conj '[[?e ?a ?v] ...]))
               (?> facets (update-in [:where] (fn [w] (into (make-dynamic-query facets) w))))
               (doto println))]
    (->> (d/q query (d/db connection) lemma)
         ffirst)))

(comment
  (time (s/with-fn-validation (get-morpheme-graph (-> diachronic-register-service.user/system :db :connection) "a" nil)))

  (time (d/q '{:find [(clojure.core/frequencies ?clemma)], :with [?cword], :in [$ ?lemma], :where [[?word :word/lemma ?lemma] [?sentence :sentence/words ?word] [?sentence :sentence/words ?cword] [?cword :word/lemma ?clemma] [(!= ?lemma ?clemma)]]}
             (d/db (-> diachronic-register-service.user/system :db :connection))
             "事"))
  ;; "Elapsed time: 804.504046 msecs" -> 446


  (bench (dorun (d/q '{:find [(clojure.core/frequencies ?clemma)], :with [?cword], :in [$ ?lemma], :where [[?paragraph :paragraph/document ?document]
                       [?paragraph :paragraph/sentences ?sentence] [?word :word/lemma ?lemma] [?sentence :sentence/words ?word] [?sentence :sentence/words ?cword] [?cword :word/lemma ?clemma] [(!= ?lemma ?clemma)]]}
                     (d/db (-> diachronic-register-service.user/system :db :connection))
                     "花")))

  ;; Execution time mean : 3.472424 sec
  ;; Execution time std-deviation : 519.436798 ms

)

(sm/defn get-morpheme-strings :- [{:lemma s/Str s/Keyword s/Num}]
  "Given a datomic connection and a vector of queries, returns the morpheme frequency profile given the query constraints.
  Queries that specify more than one possible value must do so with a vector. TODO functions (>, <, ... + arbitrary functions); make queries ordered so we can take advantage of faster explicit queries in datalog."
  [connection :- s/Any
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
                               into '[[?paragraph :paragraph/document ?document]
                                      [?paragraph :paragraph/sentences ?sentence]
                                      [?sentence :sentence/words ?word]
                                      [?word :word/lemma ?lemma]
                                      #_[?word :word/pos ?pos]])
        inputs (into [(d/db connection)] (:inputs dynamic-query))
        query (dissoc (merge-with into common-query where-query) :inputs)]
    (->> (apply d/q query inputs)
         (r/map (partial zipmap [:lemma :count :sentence-count :paragraph-count :document-count]))
         (r/map (fn [m] (assoc m :tf-idf (stats/tf-idf :rsj 10000000 (:count m) (:document-count m)))))
         (into [])
         #_(map first)
         #_frequencies)))

(comment
  (time (e! (s/with-fn-validation (get-morpheme-strings (-> diachronic-register-service.user/system :db :connection) {:document/corpus "BCCWJ"}))))
  (time (get-morpheme-strings (-> diachronic-register-service.user/system :db :connection) {:document/subcorpus ["OC" "OM"] :document/corpus "Sun"})))

(comment
  (clj-mecab.parse/with-dictionary-string :unidic (text/parse-sentence-synchronized "今日は。"))
  (-> (d/entity (d/db (-> diachronic-register-service.user/system :db :connection)) 17592186045420) (.get ":sentence/text"))
  (ns diachronic-register-service.data)
  (load-data (-> diachronic-register-service.user/system :db :connection) {:metadata-dir "/data/BCCWJ-2012-dvd1/DOC/" :corpus-dir "/data/BCCWJ-2012-dvd1/C-XML/VARIABLE/" :metadata-keys #{:audience :media :topic :gender :category :addressing :target-audience :author-year :subcorpus :basename :title :year}})
  (d/q '[:find ?e ?v :where [?e :sentence/text ?v]] (d/db (-> diachronic-register-service.user/system :db :connection))))
