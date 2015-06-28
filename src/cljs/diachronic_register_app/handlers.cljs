(ns diachronic-register-app.handlers
  (:require-macros [plumbing.core :refer [for-map]])
  (:require [schema.core :as s :include-macros true]
            [taoensso.timbre :as timbre
             :refer-macros [log  trace  debug  info  warn  error  fatal  report
                            logf tracef debugf infof warnf errorf fatalf reportf spy]]
            [plumbing.core :refer [map-vals]]

            [re-frame.core :refer [register-handler dispatch dispatch-sync subscribe]]
            [re-frame.middleware :as middleware]

            [diachronic-register-app.communication :as comm]))

(register-handler :initialize-app-state
  (fn [_ _]
    {:channel-state nil
     :query-string ""
     :morpheme nil
     :morpheme-variants nil
     :stats nil
     :facets nil
     :search-state nil
     :metadata-template nil}))

(register-handler :reset-app-state
  (fn [db _]
    (assoc db
           :query-string ""
           :morpheme nil
           :morpheme-variants nil
           :stats nil
           ;;:facets nil ;; FIXME should rather reset
           :search-state nil)))

(def opt s/optional-key)
(s/defschema TreeNode
  {s/Keyword (s/either s/Str s/Num s/Bool s/Keyword)
   (s/optional-key :children) {(s/either s/Keyword s/Str) (s/recursive #'TreeNode)}})
(s/defschema IndexedNode
  {(s/either s/Keyword s/Str)
   TreeNode})
(s/defschema D3Tree
  (s/maybe
    {:name           s/Str
     s/Keyword       s/Num
     (opt :children) [(s/recursive #'D3Tree)]}))
(s/defschema MetadataRecord
  {:name        s/Str
   :checked     s/Bool
   (opt :count) s/Num
   (opt :children) {s/Str (s/recursive #'MetadataRecord)}})
(s/defschema Metadata
  {(s/enum "document" "paragraph")
   {s/Str
    (s/either
     {(s/either s/Str s/Keyword s/Num)
      MetadataRecord}
     IndexedNode
     D3Tree)}})
(s/defschema DB
  {:channel-state (s/maybe (s/enum :ready))
   :query-string s/Str
   :morpheme (s/maybe {s/Keyword s/Str})
   :morpheme-variants (s/maybe D3Tree)
   :stats (s/maybe {s/Keyword s/Any})
   :facets (s/maybe
            {s/Keyword {:metadata         {s/Str {s/Str s/Any}}
                        (opt :statistics) (s/maybe {s/Keyword s/Any})
                        (opt :selection)  [{s/Keyword s/Any}]
                        (opt :graph)      {s/Str s/Num}}})
   :search-state (s/maybe {s/Keyword (s/enum :timeout :loading :full :query-string)})
   :metadata-template (s/maybe {s/Str {s/Str s/Any}})})

(defn valid-schema?
  "validate the given db, writing any problems to console.error"
  [db]
  (let [res (s/check DB db)]
    (if (some? res)
      (.error js/console (str "Schema problem: " res)))))

(def standard-middleware [(when ^boolean goog.DEBUG middleware/log-ex)
                          (when ^boolean goog.DEBUG middleware/debug)
                          (when ^boolean goog.DEBUG (middleware/after valid-schema?))])

(s/defn selected-facets :- [{s/Keyword s/Any #_(s/enum [(s/enum s/Str s/Num)] s/Str s/Num)}]
  [metadata :- Metadata
   #_{s/Str {s/Str {s/Any #_(s/enum s/Str s/Num) {:name s/Str :checked s/Bool}}}}]
  (vec
   (for [[nsk nskd] metadata
         [k kd] nskd
         [v vd] kd ;; <- this is where we want to dispatch on facet type (tree, OR, AND, ...)
         :when (:checked vd)]
     (do (info nsk k v)
         {(keyword nsk k) v}))))

(s/defn metadata-to-checkboxes :- Metadata
  [metadata :- {s/Keyword s/Any}]
  (println metadata)
  (->> metadata
       (group-by #(namespace (first %)))
       (map-vals (fn [xs]
                   (for-map [[nsk vs] xs]
                       (name nsk)
                     (do (println nsk vs)
                         (if (map? vs)
                           (with-meta vs {:type :tree})
                           (with-meta
                             (for-map [v vs] ;; vs should be dealt with based on datatype...
                                 v {:name (str v) :checked false})
                             {:type :list}))))))))

(register-handler :get-metadata
  standard-middleware
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
                          (dispatch-sync [:set-metadata metadata-checkboxes (:facets db)])
                          (dispatch-sync [:add-facet :facet-1])
                          (dispatch-sync [:add-facet :facet-2])))))))
    db))

(register-handler :set-metadata
  standard-middleware
  (fn [db [_ data]] (assoc db :metadata-template data)))

(register-handler :update-metadata
  standard-middleware
  (fn [db [_ path]]
    (-> db
        (update-in path not)
        (update-in (subvec path 0 2)
                   (fn [facet-map] (assoc facet-map :selection (selected-facets (:metadata facet-map))))))))

(register-handler :set-metadata-statistics
  standard-middleware
  (fn [db [_ id statistics]]
    (assoc-in db [:facets id :statistics] statistics)))

(register-handler :update-metadata-statistics
  standard-middleware
  (fn [db [_ id]]
    (let [payload (-> db :facets id :selection)]
      (comm/send! [:query/metadata-statistics payload]
                  30000                                    ;; TODO Longer timeout is for testing.
                  (fn [reply]
                    (if (= :chsk/timeout reply)
                      (warn ":query/metadata-statistics timed out")
                      (dispatch [:set-metadata-statistics id (metadata-to-checkboxes reply)])))))
    db))

(register-handler :add-facet
  standard-middleware
  (fn [db [_ facet-name]]
    (assoc-in db
              [:facets facet-name]
              {:metadata (:metadata-template db)})))

(register-handler :delete-facet
  standard-middleware
  (fn [db [_ facet-name]] (trace "Deleting facet" facet-name) (update-in db [:facets] dissoc facet-name)))

(register-handler :set-sente-connection-state
  standard-middleware
  (fn [db [_ state]] (assoc db :channel-state state)))

(register-handler :update-search-state
  standard-middleware
  (fn [db [_ ids state]]
    (trace "Updating search state" state)
    (merge db
           {:search-state
            (for-map [id ids]
                     id state)})))

(defn deep-merge-with [f & maps]
  (apply
    (fn m [& maps]
      (if (every? map? maps)
        (apply merge-with m maps)
        (apply f maps)))
    maps))
(register-handler :update-graph
  standard-middleware
  (fn [db [_ ids reply]]
    (merge-with (partial merge-with merge) ;; FIXME -> deep-merge-with
                db
                {:facets
                 (for-map [id ids]
                     id {:graph (get-in reply [:data id])})})))

(register-handler :update-stats
  standard-middleware
  (fn [db [_ reply]] (assoc db :stats (:stats reply))))


(register-handler :search-graphs
  standard-middleware
  (fn [db [_ query-string ids]]
    (let [payload (for-map [id ids]
                      id (if (not= "" query-string)
                           (into [{:word/orth-base query-string}]
                                 (-> db :facets id :selection))
                           (-> db :facets id :selection)))]
      (comm/send! [:query/graphs payload]
                  60000                                    ;; TODO Longer timeout is for testing.
                  (fn [reply]
                    (if (= :chsk/timeout reply)
                      (dispatch [:update-search-state ids :timeout])
                      (do                                   ;; FIXME
                        (dispatch [:update-graph ids reply])
                        (dispatch [:update-stats reply])
                        (dispatch [:update-search-state ids (if (= "" query-string) :full :query-string)])))))
      db)))

(register-handler :update-query-string
  standard-middleware
  (fn [db [_ query-string]] (assoc db :query-string query-string)))

(register-handler :set-morpheme-variants
  standard-middleware
  (fn [db [_ variants]] (assoc db :morpheme-variants variants)))

(register-handler :get-morpheme
  standard-middleware
  (fn [db [_ morpheme]] (assoc db :morpheme morpheme)))

(register-handler :get-morpheme-variants
  standard-middleware
  (fn [db [_ query-string]]
    (comm/send! [:query/morpheme-variants query-string]
                5000
                (fn [reply]
                  (if (keyword? reply)
                    (error "Updating morpheme variants failed" reply)
                    (dispatch [:set-morpheme-variants reply]))))
    db))
