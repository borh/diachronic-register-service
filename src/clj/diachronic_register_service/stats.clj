(ns diachronic-register-service.stats
  (:require [schema.core :as s]
            [diachronic-register-service.schemas :refer [StringNumberMap]]
            [clojure.set :as set]
            [clojure.core.reducers :as r]
            [clojure.math.combinatorics :as combo]
            [primitive-math :as p])
  (:import [clojure.lang PersistentHashSet]))

;;(set! *warn-on-reflection* true)
;;(p/use-primitive-operators)

(defn log2 [^Number n]
  (/ (Math/log n)
     (Math/log 2)))

;; TODO go with one variant and simplify
(s/defn tf-idf :- s/Num
  "http://trimc-nlp.blogspot.jp/2013/04/tfidf-with-google-n-grams-and-pos-tags.html"
  [weight-type :- (s/enum :rsj :rsj-10 :rsjc)
   N  :- s/Int ;; Equivalent to `sum_i(Del_i)` in literature.
   tf :- s/Int ;; Equivalent to `tf_i` in literature.
   df :- s/Int]
  (if (zero? tf)
    0.0
    (case weight-type
      :rsjc (Math/log (/ (+ tf 0.5)
                         (+ N 0.5)))
      ;; Manning & Schutze p. 543; Sublinear term frequency scaling. See Manning, Raghavan, & Schutze p. 116.
      :rsj  (* (inc (log2 tf))
               (log2 (/ N df)))
      :rsj-10 (* (inc (Math/log10 tf))
                 (Math/log10 (/ N df))))))

(s/defn pairwise-difference :- Double
  [xs :- [s/Num]]
  (r/fold
   (r/monoid
    (fn [a [x1 x2]] (+ a (Math/abs (double (- x1 x2)))))
    (fn [] 0))
   (combo/combinations xs 2)))

(s/defn DP :- s/Num
  "For a word A in a corpus X, DP is computed as follows:
  1. compute the size of each part of X (in % of all of X); i.e. part = contiguous metadata region?
  2. compute the relative frequency of A in each part of X; i.e. P(a|X)
  3. compute the absolute pairwise differences between the sizes and the relative frequencies, sum them, and divide the sum by two.
  DP is close to 0 when A is distributed evenly, and close to 1 when A is distributed unevenly/clumpily."
  [ms :- [{:document/length Long :tf Long}]]
  (let [total-size (r/fold
                    (r/monoid
                     (fn [a x] (+ a (:document/length x)))
                     (fn [] 0))
                    ms)
        total-frequency (reduce clojure.core/+ (map :tf ms))
        relative-sizes (map #(/ % total-size) (map :document/length ms))
        relative-frequencies (map (fn [r-size tf] (/ tf total-frequency)) relative-sizes #_FIXME (map :tf ms))]
    (/ (+ (pairwise-difference relative-sizes)
          (pairwise-difference relative-frequencies))
       2.0)))

(comment
  (s/with-fn-validation (DP [{:document/length 10 :tf 1} {:document/length 20 :tf 1}])))


(s/defn jaccard-similarity :- Double
  [graph-a :- {s/Str s/Num}
   graph-b :- {s/Str s/Num}]
  (let [vertex-a (set (keys graph-a))
        vertex-b (set (keys graph-b))
        common (set/intersection vertex-a vertex-b)
        union (set/union vertex-a)]
    (if (not-empty union)
      (double (/ (count common)
                 (count union)))
     0.0)))

(s/defschema MorphemeStats
  {:word   s/Str
   s/Keyword {:count  s/Num
              (s/optional-key :tf-idf) s/Num}})
(s/defschema GraphStats
  {s/Keyword
   {:unique-words {s/Str s/Num}
    :unique-prop  Double}
   :common-words [MorphemeStats]
   :common-prop  Double})

(s/defn ->morpheme-stats :- [MorphemeStats]
  [m :- StringNumberMap]
  (r/fold
   (r/monoid
    (fn [accum word-freq]
      (let [[word freq] (first word-freq)]
        {:word word :count freq :tf-idf (tf-idf :rsj 10000000 freq freq)}))
    (fn []))
   m))

(s/defn generic-diff :- GraphStats
  "Returns a map of different properties that represent differences between the two graphs a and b."
  [facet-map :- {s/Keyword {:graph StringNumberMap}}]
  (let [[a-id b-id] (keys facet-map)
        a (-> facet-map a-id :graph)
        b (-> facet-map b-id :graph)
        a-vocab (set (keys a))
        b-vocab (set (keys b))
        common-vocab (set/intersection a-vocab b-vocab)
        total-vocab  (set/union        a-vocab b-vocab)
        a-only-vocab (set/difference   a-vocab b-vocab)
        b-only-vocab (set/difference   b-vocab a-vocab)]
    (println a)
    {a-id {:unique-words (select-keys a a-only-vocab)
           :unique-prop (if-not (empty? a-vocab) (double (/ (count a-only-vocab) (count a-vocab))))}
     b-id {:unique-words (select-keys b b-only-vocab)
           :unique-prop (if-not (empty? b-vocab) (double (/ (count b-only-vocab) (count b-vocab))))}
     :common-words (sort-by :word
                            (for [v common-vocab]
                              {:word v
                               a-id  {:count (get a v)}
                               b-id  {:count (get b v)}}))
     :common-prop  (if (empty? total-vocab) 0.0 (double (/ (count common-vocab) (count total-vocab))))}))
