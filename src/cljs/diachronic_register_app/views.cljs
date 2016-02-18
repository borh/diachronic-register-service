(ns diachronic-register-app.views
  (:require [schema.core :as s :include-macros true]
            [clojure.string :as str]
            [goog.string :as gstr]
            ;;[clojure.pprint :refer [pprint]]

            [re-frame.core :refer [dispatch dispatch-sync subscribe]]
            [reagent.core :as reagent]

            [taoensso.timbre :as timbre
             :refer-macros [log  trace  debug  info  warn  error  fatal  report
                            logf tracef debugf infof warnf errorf fatalf reportf spy]]

            [diachronic-register-app.translations :refer [ja->en]]
            [diachronic-register-app.handlers :refer [D3Tree IndexedTree]]
            [diachronic-register-app.force :as force]))

(comment
  (defmulti make-selection-element (fn [coll] (:match-type (meta coll))))

  (defmethod make-selection-element :OR
    [id nk k vs]
    [:div.checkbox-inline
     (for [[v v-map] vs]
       ^{:key (str nk k (:name v-map))}
       [:label.checkbox-inline
        [:input
         {:type      "checkbox"
          :id        (str nk k (:name v-map))
          :value     (:name v-map)
          :checked   (:checked v-map)
          :on-change (fn [_] (trace "Updating state:" (:checked v-map) "to" (not (:checked v-map))) (dispatch [:update-metadata [:metadata id nk k v :checked]]))}
         (:name v-map)]])]))

(comment

  ;; The following would make choosing a value to add to the query easy?
  [:select.form-control
   [:option 1]
   [:option 2]
   [:option 3]]

  )

(defn prettify-facet-name [s]
  (-> (if (or (string? s) (keyword? s)) s (str s))
      name
      (str/replace "-" " ")
      str/capitalize))

(defn make-collapsible-panel ;; FIXME open? not working?
  [{:keys [panel-name open?]} body]
  (let [unique-id (gensym (name panel-name))]
    (with-meta
      [:div.panel.panel-primary {:id (str "panel" unique-id)}
       [:div.panel-heading {:role "tab" :id (str "heading" unique-id)}
        [:h3.panel-title
         [:a {:data-toggle   "collapse"
              :data-parent   (str "#panel" unique-id)
              :href          (str "#collapse" unique-id)
              :aria-expanded (if open? false true)
              :aria-controls (str "collapse" unique-id)}
          (prettify-facet-name panel-name)]]]
       [:div.panel-collapse.collapse.in ;; <- will open on load
        {:id (str "collapse" unique-id)
         :role "tabpanel"
         :aria-labelledby (str "heading" unique-id)}
        [:div.panel-body
         body]]]
      {:key unique-id})))

(s/defn render-tree
  [id :- s/Keyword
   path :- (s/maybe [s/Any])
   current-node :- (s/either s/Str s/Keyword)
   tree :- IndexedTree]
  ;;(println "Path: " path)
  ;;(println "B: " tree)
  (let [tree (get-in tree [current-node])]
    ;;(println "A: " tree)
    ^{:key (apply str path)}
    [:div.row
     [:div.checkbox-inline
      [:label.checkbox-inline
       [:input
        {:type      "checkbox"
         :id        (:name tree)
         :value     (:name tree)
         :hidden    (zero? (:count tree))
         :checked   (:checked tree) ;; TODO {:indeterminate true} if not all children are checked
         :on-change (fn [_]
                      (dispatch [:update-metadata-cascade (into [:facets id :metadata "document" "category"] path)]) ;; Need to check/uncheck all children
                      #_(dispatch [:update-metadata-statistics id]))} ;; This breaks somehow...
        (str (:name tree) " ") [:span.badge (:count tree)] ;; TODO translation hook
        (for [child (:children tree)]
          (render-tree
           id
           (into path [:children (first child)])
           (first child)
           (apply hash-map child)))]]]]))


(defn facet-box
  [id]
  (let [facets (subscribe [:facets])]
    (fn []
      (if (empty? @facets)
        [:p "Loading metadata..."]
        [:div.row
         ;; TODO tree zipper metadata + dynamic controls depending on datatype (year: date span, #{:OC.. ..} set support, etc.)
         (make-collapsible-panel
          {:panel-name id
           :open? true}
          (for [[metadata-level kvs] (-> @facets id :metadata)]
            (make-collapsible-panel
             {:panel-name metadata-level
              :open? true}
             (for [[metadata-name vs] kvs]
               ^{:key metadata-name}
               [:div.row
                [:div.form-group;;.col-md-12
                 [:p (str/capitalize metadata-name)]
                 ;; <- this is where we want to dispatch on facet type (tree, OR, AND, ...)
                 (case (:type (meta vs))
                   :tree (render-tree id ["Categories"] "Categories" vs)
                   :list
                   [:div.checkbox-inline
                    (for [[v v-map] vs]
                      ^{:key (str metadata-level metadata-name (:name v-map))}
                      [:label.checkbox-inline
                       [:input
                        {:type      "checkbox"
                         :id        (str metadata-level metadata-name (:name v-map))
                         :value     (:name v-map)
                         :checked   (:checked v-map)
                         :on-change (fn [_]
                                      (dispatch [:update-metadata [:facets id :metadata metadata-level metadata-name v :checked]])
                                      #_(dispatch [:update-metadata-statistics id]))}
                        (ja->en metadata-level metadata-name (:name v-map)) " " [:span.badge (:count v-map)]]])])]]))))]))))

(defn search-box ;; FIXME need to separate intents when not dealing with word-based search
  "Query box."
  []
  (let [query-string (subscribe [:query-string])
        facets (subscribe [:facets])]
    (fn []
      [:div.col-md-4.col-md-offset-4.input-group
       [:input {:class        "input form-control" :type "text"
                :placeholder  "Input word or leave blank to query all"
                :value        @query-string
                :on-change    (fn [e]
                                (let [query-string-string (.. e -target -value)]
                                  (dispatch [:update-query-string query-string-string])
                                  (dispatch [:get-morpheme-variants query-string-string])
                                  (dispatch [:get-morpheme-sentences query-string-string])))
                :on-key-press (fn [e]
                                (when (== (.-keyCode e) 13)
                                  (let [query-string-string (.. e -target -value)] ;; FIXME do we need to get query-string here again? -> subscription value should be enough
                                    (dispatch [:update-search-state (keys @facets) :loading])
                                    (dispatch [:search-graphs query-string-string (keys @facets)]))))}]
       [:span.input-group-btn
        [:button {:class    "btn" :id "search-btn" :type "button"
                  :on-click (fn [_]
                              (dispatch [:update-search-state (keys @facets) :loading])
                              (dispatch [:search-graphs @query-string (keys @facets)]))}
         "Search"]]])))

;; # Tree component
(defn tree-box-render [id]
  (trace "Rendering tree-box" id)
  (fn []
    [:div {:id (str "d3-tree-" (name id)) :react-key (str "d3-tree-" (name id))}]))

(defn tree-box-did-mount [id d3-tree]
  (trace "tree-box-did-mount")
  (fn []
    (force/make-tree-graph! id d3-tree)))

(defn tree-box
  [id d3-tree]
  (reagent/create-class {:display-name (str "tree-box" (name id))
                         :reagent-render (tree-box-render id)
                         :component-did-mount (tree-box-did-mount id d3-tree)
                         :component-did-update (fn [this]
                                                 (let [[_ data] (reagent/argv this)]
                                                   (tree-box-did-mount id d3-tree)))}))

(defn morpheme-variants-box []
  (let [morpheme-variants (subscribe [:morpheme-variants])]
    (fn []
      (if (pos? (:count @morpheme-variants))
        [:div.row [tree-box "query" @morpheme-variants]]))))

(defn regex-modifiers
  "Returns the modifiers of a regex, concatenated as a string."
  [re]
  (str (if (.-multiline re) "m")
       (if (.-ignoreCase re) "i")))
(s/defn re-pos;; :- [[s/Num s/Str]]
  "Returns a vector of vectors, each subvector containing in order:
   the position of the match, the matched string, and any groups
   extracted from the match."
  [re :- js/RegExp s :- s/Str]
  (let [re (js/RegExp. (.-source re) (str "g" (regex-modifiers re)))]
    (loop [res []]
      (if-let [m (.exec re s)]
        (recur (conj res (vec (cons (.-index m) m))))
        res))))
(defn morpheme-sentences-box []
  (let [sentences (subscribe [:morpheme-sentences])
        query-string (subscribe [:query-string])]
    (fn []
      (if @sentences
        (let [q @query-string]
          [:div
           [:ol
            (for [[n sentence] (zipmap (range) @sentences)]
              ;; FIXME http://stackoverflow.com/questions/18735665/how-can-i-get-the-positions-of-regex-matches-in-clojurescript
              (with-meta
                (into [:li]
                      (loop [matched-indexes (map first (re-pos (re-pattern q) sentence))
                             current-index 0
                             html []]
                        ;;(println matched-indexes current-index html)
                        (if (seq matched-indexes)
                          (let [matched-index (first matched-indexes)
                                sentence-part (subs sentence current-index matched-index)]
                            (recur (next matched-indexes)
                                   (+ matched-index (count q))
                                   (if (not-empty sentence-part)
                                     (conj html sentence-part [:b.text-success q])
                                     (conj html [:b.text-success q]))))
                          (if (<= current-index (count sentence))
                            (conj html (subs sentence current-index))
                            html))))
                #_(if (= sentence q)
                      [:li [:b.text-success q]]
                      (into [:li]
                            (interpose [:b.text-success q]
                                       (clojure.string/split sentence (re-pattern q)))))
                {:key (str "sentence" n)}))]])))))

;; # Network component
(defn graph-box-render [id]
  (info "Rendering graph-box" id)
  (fn []
    [:div {:id (str "d3-graph-" (name id)) :react-key (str "d3-graph-" (name id))}]))

(defn graph-box-did-mount [id query-string graph]
  (info "graph-box-did-mount" id query-string)
  (fn []
    (force/make-force-graph! id query-string graph)))

(defn graph-box
  [id query-string graph]
  (reagent/create-class {:display-name (str "graph-box-" (name id))
                         :reagent-render (graph-box-render id)
                         :component-did-mount (graph-box-did-mount id query-string graph)}))

;; Facet information
(defn facet-info-box
  []
  (let [facets (subscribe [:facets])]
    (fn []
      [:div
       (for [[facet-id facet-kvs] @facets]
         ^{:key (str "info" facet-id)}
         [:div
          [:p "Metadata information for " (prettify-facet-name facet-id)]
          (let [selected-vec (-> facet-kvs :selection)]
            (for [selected selected-vec
                  [metadata-ns metadata-v] selected
                  :let [metadata-top (namespace metadata-ns)
                        metadata-bottom (name metadata-ns)]]
              [:div (str metadata-top "/" metadata-bottom ": " metadata-v " ")
               [:span.badge (get-in facet-kvs [:statistics metadata-top metadata-bottom metadata-v :count])]]))])])))

(defn results-box
  "Renders results given query-string and metadata query."
  [id]
  (let [search-state (subscribe [:search-state])
        query-string (subscribe [:query-string])
        facets (subscribe [:facets])]
    (fn []
      [:div
       (case (-> @search-state id)
         :loading [:p "Searching..."]
         :timeout [:p "Search timed out. Please try again or make a more constrained query."]
         nil      [:p "Please enter search query."]
         :failed  [:p "Search timed out. Please try again!"]

         :full
         (if (not-empty (-> @facets id :metadata))
           [:div
            [:div [:p (str "Results for " (prettify-facet-name id) " with metadata ")
                   (for [kv (-> @facets id :selection)]
                     (let [[k v] (first kv)]
                       ^{:key (str kv)} [:span.label.label-default (str (prettify-facet-name k) ": " v)]))]]
            #_(if (not-empty (-> @facets id :data :graph))
              [graph-box id @query-string (-> @facets id :data :graph)])]   ;; TODO would making the d3 node at this level help?
           [:p "No results found."])

         :query-string
         (if (not-empty (-> @facets id :data :graph))
           [:div [:p (str "Results for " (prettify-facet-name id) " with metadata ")
                  (for [kv (-> @facets id :selection)]
                    (let [[k v] (first kv)]
                      ^{:key (str kv)} [:span.label.label-default (str (prettify-facet-name k) ": " v)]))]
            [:table.table
             [:thead [:tr [:th "Query-String"] [:th "Frequency"]]]
             [:tbody
              (for [[k v] (-> @facets id :data :graph)]          ;; TODO variable table columns
                ^{:key (str k v)}
                [:tr [:td k] [:td v]])]]]
           [:p "No results found."]))])))

;; FIXME: Add popover functionality on words that shows relevant information.
;; <button type="button" class="btn btn-default" data-container="body" data-toggle="popover" data-placement="top" data-content="Vivamus sagittis lacus vel augue laoreet rutrum faucibus.">Top</button>

(defn make-table [title headers xs]
  [:div.col-md-4
   [:div.panel.panel-default
    [:div.panel-heading title]
    [:table.table.table-condensed.table-responsive
     [:thead (into [:tr] (mapv (fn [text] [:th text]) headers))]
     [:tbody
      (for [[k v] (take 50 (sort-by second > xs))]
        ^{:key (str k v)}
        [:tr [:td k] [:td v]])]]]])

(defn make-map-table [title headers ids xs]
  (info xs)
  [:div.col-md-4
   [:div.panel.panel-default
    [:div.panel-heading title]
    [:table.table.table-condensed.table-responsive
     [:thead (into [:tr [:th (first headers)]] (mapv (fn [text id] [:th text " in " (prettify-facet-name id)]) (rest headers) ids))]
     [:tbody
      (for [{:keys [word] :as m}
            (take 50 (sort-by
                      (fn [x]
                        (apply +
                               (for [[k v] x
                                     :let [cnt (:count v)]
                                     :when cnt] cnt))) > xs))]
        ^{:key (str word)}
        [:tr (into [:td word] (mapv (fn [id] [:td (-> m id :count)]) ids))])]]]])

(defn stats-box []
  (let [stats (subscribe [:stats])
        facets (subscribe [:facets])]
    (fn []
      [:div.col-md-12 ;; TODO: do we want to visualize the 1.0 and 3.0 as a sorted line of words (order determined by some weight....)?
       (if @stats
         (let [{:keys [common-prop
                       common-words]}
               @stats

               [a-id b-id] (sort (keys @facets))]
           [:div
            [:div.row
             [make-table (str "Words occurring only in " (prettify-facet-name a-id)) ["Word" "Frequency"] (-> @facets a-id :data :unique-words)]
             [make-map-table (str "Words common to " (prettify-facet-name a-id) " and " (prettify-facet-name b-id)) ["Word" "Frequency" "Frequency"] [a-id b-id] (-> @stats :common-words)]
             [make-table (str "Words occurring only in " (prettify-facet-name b-id)) ["Word" "Frequency"] (-> @facets b-id :data :unique-words)]]
            ;;[:p "Common: " "(50/" (count common) ") " (str/join ", " (take 50 common))]
            ;;[:p "A only: " "(50/" (count a-only) ") " (str/join ", " (take 50 a-only))]
            ;;[:p "B only: " "(50/" (count b-only) ") " (str/join ", " (take 50 b-only))]
            [:p "Common proportion: " common-prop]
            [:p (prettify-facet-name a-id) " unique proportion: " (-> @facets a-id :data :unique-prop)]
            [:p (prettify-facet-name b-id) " unique proportion: " (-> @facets b-id :data :unique-prop)]])
         [:p.text-center "Statistics = " "(waiting...)"])])))

(defn stats-small-box []
  (let [stats (subscribe [:stats])
        facets (subscribe [:facets])]
    (fn []
      #_(let [xs
            (into []
                  (or (* 100 (-> @stats :common-prop)) 0)
                  (flatten
                   (for [facet @facets
                         [facet-name facet-map] facet]
                     [(prettify-facet-name facet-name)
                      (or (* 100 (-> facet-map :statistics :unique-prop)) 0)])))]
        [:div
         (apply (fn [[c a af b bf]] (gstr/format "Common: %f%, %s only: %f%, %s only: %f%" c a af b bf)) xs)]))))

(defn navbar []
  [:nav.navbar.navbar-default.navbar-fixed-top.centered
   {:role "navigation"}
   [:div.container-fluid
    [:div.navbar-header
     [:button.navbar-toggle {:type "button" :data-toggle "collapse" :data-target "#navbar-top-collapse"}
      [:span.sr-only "Toggle navigation"]
      [:span.icon-bar]
      [:span.icon-bar]
      [:span.icon-bar]]
     [:a.navbar-brand
      {:href "#"}
      "Japanese Language Diachronic Register Search"]]
    [:div.collapse.navbar-collapse {:id "navbar-top-collapse"}
     [:div.nav.navbar-nav.navbar-right
      [:ul.nav.navbar-nav
       [:li [:a {:href "https://github.com/borh/diachronic-register-service"} "Help"]]
       [:li [:a {:href "#" :on-click (fn [_] (dispatch [:reset-app-state]))} "Reset"]]
       #_[:li [:a {:href "#"} "Login"]]]]
     #_[:span.navbar-text.navbar-right "Signed-in as Anonymous"]]]])

(defn footer []
  [:nav.navbar.navbar-default.navbar-fixed-bottom.centered
   {:role "navigation"}
   [:div.container-fluid
    [:div.navbar-header
     [:button.navbar-toggle {:type "button" :data-toggle "collapse" :data-target "#navbar-bottom-collapse"}
      [:span.sr-only "Toggle navigation"]
      [:span.icon-bar]
      [:span.icon-bar]
      [:span.icon-bar]]
     [:a.navbar-brand
      {:href "#"}
      "Statistics"]
     [:span.navbar-text
      [stats-small-box]]]
    [:div.collapse.navbar-collapse {:id "navbar-bottom-collapse"}
     [:div.nav.navbar-nav
      [:ul.nav.navbar-nav
       [:li [:a "Details"]]]]
     [:p.navbar-text.navbar-right "JLDRS © Bor Hodošček | " [:a {:href "https://github.com/borh/diachronic-register-service"} "Source code"]]]]])

(defn app []
  (let [app-state-ready? (subscribe [:app-state-ready?])
        sente-connected? (subscribe [:sente-connected?])
        facets (subscribe [:facets])]
    (fn []
      [:div.container-fluid
       [navbar]
       [:div.page-header
        [:h1.text-center "Japanese Language Diachronic Register Search"]]

       ;; Search
       [:div.row
        (if-not @app-state-ready?
          [:p "Loading search-box"]
          [search-box])]
       [:div.row.voffset [morpheme-variants-box]]
       [:div.row [morpheme-sentences-box]]

       ;; Facet
       (if-not (and @app-state-ready? @sente-connected? (not-empty @facets))
         [:p "Loading facet-box"]
         (into [:div.row.voffset]
               (mapv
                (fn [[id _]]
                  [:div {:class (str "col-md-" (int (/ 12 (count @facets))) " hoffset")} ;;.col-md-6
                   [facet-box id]])
                (sort @facets))))

       ;; Facet selection information
       (when (not-empty @facets)
         ;; FIXME Do we want to start with the empty (all) selection, or wait for the user to select something?
         [:div.row.voffset [facet-info-box]])

       ;; Results
       (into
        [:div.row.voffset [stats-box]]
        (mapv
         (fn [[id _]]
           [:div {:class (str "col-md-" (int (/ 12 (count @facets))))} ;;.col-md-6
            [results-box id]])
         @facets))

       [footer]])))
