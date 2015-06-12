(ns diachronic-register-app.handlers
  (:require-macros [plumbing.core :refer [for-map]])
  (:require [schema.core :as s :include-macros true]
            [taoensso.timbre :as timbre
             :refer-macros [log  trace  debug  info  warn  error  fatal  report
                            logf tracef debugf infof warnf errorf fatalf reportf spy]]
            [plumbing.core :refer [map-vals]]

            [re-frame.core :refer [register-handler dispatch subscribe]]

            [diachronic-register-app.communication :as comm]))

(register-handler
  :initialize-app-state
  (fn [_ _]
    {:channel-state nil
     :lemma ""
     :morpheme-variants nil
     :stats nil
     :graph {:a {}
             ;;:b {}
             }
     :search-state {:a :loading
                    :b :loading}
     :metadata nil #_{:a nil
                      :b nil}}))

(register-handler :set-sente-connection-state (fn [db [_ state]] (assoc db :channel-state state)))

(register-handler
  :update-search-state
  (fn [db [_ ids state]]
    (trace "Updating search state" state)
    (merge db
           {:search-state
            (for-map [id ids]
                     id state)})))

(register-handler
  :update-graph
  (fn [db [_ ids reply]]
    (trace "Updating graphs")
    (merge db
           {:graph
            (for-map [id ids]
                     id (get-in reply [:data (id {:a 0 :b 1})]))})))

(register-handler :update-stats (fn [db [_ ids reply]] (assoc db :stats (:stats reply))))

(s/defn selected-facets :- [{s/Keyword (s/enum [(s/enum s/Str s/Num)] s/Str s/Num)}]
        [metadata :- {s/Keyword {s/Keyword {(s/enum s/Str s/Num) {:name s/Str :checked s/Bool}}}}]
        (vec
          (for [[nsk nskd] metadata
                [k kd] nskd
                [v vd] kd ;; <- this is where we want to dispatch on facet type (tree, OR, AND, ...)
                :when (:checked vd)]
            (do (trace nsk k v)
                {(keyword nsk k) v}))))

(register-handler
  :search-graphs
  (fn [db [_ lemma ids]]
    (let [payload (mapv
                    (fn [id]
                      (if (not= "" lemma)
                        (into [{:word/lemma lemma}]
                              (selected-facets (-> db :metadata id)))
                        (selected-facets (-> db :metadata id))))
                    ids)]
      (trace "PAYLOAD:" payload)
      (comm/send! [:query/graphs payload]
                  300000                                    ;; TODO Longer timeout is for testing.
                  (fn [reply]
                    (if (= :chsk/timeout reply)
                      (dispatch [:update-search-state ids :timeout])
                      (do                                   ;; FIXME
                        (dispatch [:update-graph ids reply])
                        (dispatch [:update-stats reply])
                        (dispatch [:update-search-state ids (if (= "" lemma) :full :lemma)])))))
      db)))

(register-handler :set-metadata (fn [db [_ data]] (trace "Setting metadata") (assoc db :metadata {:a data :b data})))

(register-handler :update-metadata (fn [db [_ path]] (trace "Setting state" (get-in db path) "at path" path) (update-in db path not)))

(register-handler :update-lemma (fn [db [_ lemma]] (trace "Updating lemma" lemma) (assoc db :lemma lemma)))

(register-handler :set-morpheme-variants (fn [db [_ variants]] (trace "Updating morpheme variants" variants) (assoc db :morpheme-variants variants)))

(register-handler
 :get-morpheme-variants
 (fn [db [_ lemma]]
   (trace "Updating morpheme variants" lemma)
   (comm/send! [:query/morpheme-variants lemma]
               5000
               (fn [reply]
                 (if (keyword? reply)
                   (error "Updating morpheme variants failed" reply)
                   (dispatch [:set-morpheme-variants reply]))))
   db))

(s/defn metadata-to-checkboxes :- {(s/enum "document" "paragraph") {s/Str {s/Any {:name s/Str :checked s/Bool}}}}
  [metadata :- [{s/Keyword s/Any}]]
  (->> metadata
       (group-by #(namespace (first %)))
       (map-vals (fn [xs]
                   (for-map [[nsk vs] xs]
                     (name nsk)
                     (for-map [v vs] ;; vs should be dealt with based on datatype...
                       v {:name (str v) :checked false}))))))

(register-handler
  :get-metadata
  (fn [db _]
    (if (nil? (:metadata db))
      (if-not (= :ready (:channel-state db))
        (dispatch [:get-metadata])
        (comm/send! [:query/all-metadata :_]
                    5000
                    (fn [reply]
                      (if (keyword? reply)
                        (do (trace "metadata failed" reply)
                            #_(get-metadata))
                        (let [metadata-checkboxes (metadata-to-checkboxes reply)]
                          ;; FIXME Hack to save on server queries.
                          (trace "metadata recieved." #_metadata-checkboxes)
                          (dispatch [:set-metadata metadata-checkboxes])))))))
    db))
