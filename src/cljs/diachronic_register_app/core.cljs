(ns diachronic-register-app.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]
                   [plumbing.core :refer [for-map]])
  (:require [cljs.core.match]
            [cljs.core.async :refer [<! >! put! chan]]



            [reagent.core :as reagent :refer [atom]]
            [re-frame.core :refer [subscribe
                                   dispatch
                                   dispatch-sync]]

            [diachronic-register-app.handlers :as handlers]
            [diachronic-register-app.subs :as subs]
            [diachronic-register-app.views :as views]
            [diachronic-register-app.communication :as comm]

            [schema.core :as s :include-macros true]))

(enable-console-print!)

#_(s/defschema GraphStats
  {:common PersistentHashSet
   :a-only PersistentHashSet
   :b-only PersistentHashSet
   :common-prop   s/Num
   :a-unique-prop s/Num
   :b-unique-prop s/Num})


(comment ;; TODO
  (defprotocol SwitchBox
    (add-constraint [a])
    (remove-constraint [a]))

  (s/defrecord TimeSpan
      [start :- s/Int
       end :- s/Int]))

;; (defn tree-facet
;;   "Generates a tree facet showing only selected leaves and minimal path."
;;   [tree]
;;   [:ul ])
;;
;; (defn or-facet
;;   "Generates an OR facet showing only attributes."
;;   [tree]
;;   [:ul ])
;;
;; (defn and-facet
;;   "Generates an AND facet showing only selected attributes."
;;   [tree]
;;   [:ul ])

(defn main []
  ;; Sente websocket communication:
  (comm/start-router!)

  (dispatch-sync [:initialize-app-state])
  (dispatch-sync [:get-metadata])

  (s/with-fn-validation                                     ;; Remove for production.
    (reagent/render [views/app] (.getElementById js/document "app"))))