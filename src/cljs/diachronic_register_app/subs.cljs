(ns diachronic-register-app.subs
  (:require-macros [reagent.ratom :refer [reaction]])
  (:require [re-frame.core :refer [register-sub]]))

(register-sub :app-state-ready? (fn [db] (reaction (not (empty? @db)))))

(register-sub :sente-connected? (fn [db] (reaction (= :ready (:channel-state @db)))))

(register-sub :initialized? (fn [db] (reaction (and (not (empty? @db)) (= :ready (:channel-state @db))))))

(register-sub :facets (fn [db] (reaction (:facets @db))))

(register-sub :search-state (fn [db] (reaction (:search-state @db))))

(register-sub :metadata (fn [db] (reaction (:metadata @db))))

(register-sub :lemma (fn [db] (reaction (:lemma @db))))

(register-sub :morpheme-variants (fn [db] (reaction (:morpheme-variants @db))))

(register-sub :graph (fn [db] (reaction (:graph @db))))

(register-sub :stats (fn [db] (reaction (:stats @db))))
