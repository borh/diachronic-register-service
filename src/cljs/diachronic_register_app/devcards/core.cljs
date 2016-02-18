(ns diachronic-register-app.devcards.core
  (:require [devcards.core]
            [reagent.core :as reagent]
            [re-frame.db :as db]
            [diachronic-register-app.core]
            [diachronic-register-app.views :as v]
            [cljs.test :as t :include-macros true :refer-macros [testing is]])
  (:require-macros
   ;; Notice that I am not including the 'devcards.core namespace
   ;; but only the macros. This helps ensure that devcards will only
   ;; be created when the :devcards is set to true in the build config.
   [devcards.core :as dc :refer [defcard deftest start-devcard-ui!]]))

(enable-console-print!)

(dc/start-devcard-ui!)

(defcard Facets
  (reagent/as-element [v/facet-box :facet-1])
  db/app-db
  {:hidden true})

(deftest facets-test
  "Tests"
  (testing "Facets test"
      (is (= 1 1))))

(defcard Search
  (reagent/as-element [v/search-box])
  db/app-db
  #_(reagent/atom
   {:channel-state nil
    :query-string ""
    :morpheme nil
    :morpheme-variants nil
    :stats nil
    :facets nil
    :search-state nil
    :metadata-template nil})
  {:inspect-data #_true false :history true})
