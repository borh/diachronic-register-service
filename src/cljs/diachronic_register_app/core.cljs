(ns diachronic-register-app.core
  (:require [schema.core :as s :include-macros true]
            [taoensso.timbre :as timbre
             :refer-macros [log  trace  debug  info  warn  error  fatal  report
                            logf tracef debugf infof warnf errorf fatalf reportf spy]]

            [reagent.core :as reagent]
            [re-frame.core :refer [subscribe
                                   dispatch
                                   dispatch-sync]]

            [diachronic-register-app.handlers :as handlers]
            [diachronic-register-app.subs :as subs]
            [diachronic-register-app.views :as views]
            [diachronic-register-app.communication :as comm]))

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

(info "Starting application...")
;; Sente websocket communication:
(comm/start-router!)

(dispatch-sync [:initialize-app-state])
(dispatch-sync [:get-metadata])
;;(dispatch-sync [:add-facet :facet-1])
;;(dispatch-sync [:add-facet :facet-2])

(s/set-fn-validation! ^boolean goog.DEBUG)
(reagent/render [views/app] (.getElementById js/document "app"))
