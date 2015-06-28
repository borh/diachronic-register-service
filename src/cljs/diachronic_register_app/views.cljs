(ns diachronic-register-app.views
  (:require [schema.core :as s :include-macros true]
            [clojure.string :as str]
            [goog.string :as gstr]

            [re-frame.core :refer [dispatch subscribe]]
            [reagent.core :as reagent]

            [taoensso.timbre :as timbre
             :refer-macros [log  trace  debug  info  warn  error  fatal  report
                            logf tracef debugf infof warnf errorf fatalf reportf spy]]

            [diachronic-register-app.handlers :refer [D3Tree TreeNode IndexedNode]]
            [diachronic-register-app.force :as force]))

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
       (:name v-map)]])])

(comment

  ;; The following would make choosing a value to add to the query easy?
  [:select.form-control
   [:option 1]
   [:option 2]
   [:option 3]]

  )

(defn prettify-facet-name [s]
  (-> s
      name
      (str/replace "-" " ")
      str/capitalize))

(defn make-collapsible-panel
  [{:keys [panel-name open?]} body]
  (let [unique-id (gensym (name panel-name))]
    (with-meta
      [:div.panel.panel-primary {:id (str "panel" unique-id)}
       [:div.panel-heading {:role "tab" :id (str "heading" unique-id)}
        [:h3.panel-title
         [:a {:data-toggle   "collapse"
              :data-parent   (str "#panel" unique-id)
              :href          (str "#collapse" unique-id)
              :aria-expanded (if open? true false)
              :aria-controls (str "collapse" unique-id)}
          (prettify-facet-name panel-name)]]]
       [:div.panel-collapse.collapse;;.in <- will open on load
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
   tree :- IndexedNode]
  ;;(println "Path: " path)
  ;;(println "B: " tree)
  (let [tree (get-in tree [current-node])]
    ;;(println "A: " tree)
    ^{:key (apply str path)}
    [:div.checkbox-inline
     [:label.checkbox-inline
      [:input
       {:type      "checkbox"
        :id        (:name tree)
        :value     (:name tree)
        :checked   (:checked tree)
        :on-change (fn [_]
                     (dispatch [:update-metadata (conj (into [:facets id :metadata "document" "category"] path) :checked)])
                     (dispatch [:update-metadata-statistics id]))}
       (str (:name tree) " ") [:span.badge (:count tree)]
       (for [child (:children tree)]
         (render-tree
          id
          (into path [:children (first child)])
          (first child)
          (apply hash-map child)))]]]))


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
                                      (dispatch [:update-metadata-statistics id]))}
                        (:name v-map)]])])]]))))]))))

(defn search-box
  "Query box."
  []
  (let [query-string (subscribe [:query-string])
        facets (subscribe [:facets])]
    (fn []
      [:div.col-md-4.col-md-offset-4.input-group
       [:input {:class        "input form-control" :type "text"
                :placeholder  "Input any character string ..."
                :value        @query-string
                :on-change    (fn [e]
                                (let [query-string-string (.. e -target -value)]
                                  (dispatch [:update-query-string query-string-string])
                                  (dispatch [:get-morpheme-variants query-string-string])))
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
                         :component-did-mount (tree-box-did-mount id d3-tree)}))

(defn morpheme-variants-box []
  (let [morpheme-variants (subscribe [:morpheme-variants])]
    (fn []
      (if (pos? (:count @morpheme-variants))
        [:div.row [tree-box "query" @morpheme-variants]]))))

(defn morpheme-sentences-box []
  (let [morpheme (subscribe [:morpheme])]
    (fn []
      (when morpheme
        [:div (for [sentence (:sentences morpheme)]
                [:p sentence])]))))

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
          [:p (pr-str (:statistics facet-kvs))]])])))

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
         nil [:p "Please enter search query."]

         :failed [:p "Search timed out. Please try again!"]

         :full
         (if (not-empty (-> @facets id :metadata))
           [:div
            [:div [:p (str "Results for " (prettify-facet-name id) " with metadata ")
                   (for [kv (-> @facets id :selection)]
                     (let [[k v] (first kv)]
                       ^{:key (str kv)} [:span.label.label-default (str (prettify-facet-name k) ": " v)]))]]
            (if (not-empty (-> @facets id :graph))
              [graph-box id @query-string (-> @facets id :graph)])]   ;; TODO would making the d3 node at this level help?
           [:p "No results found."])

         :query-string
         (if (not-empty (-> @facets id :graph))
           [:div [:p (str "Results for " (prettify-facet-name id) " with metadata ")
                  (for [kv (-> @facets id :selection)]
                    (let [[k v] (first kv)]
                      ^{:key (str kv)} [:span.label.label-default (str (prettify-facet-name k) ": " v)]))]
            [:table.table
             [:thead [:tr [:th "Query-String"] [:th "Frequency"]]]
             [:tbody
              (for [[k v] (-> @facets id :graph)]          ;; TODO variable table columns
                ^{:key (str k v)}
                [:tr [:td k] [:td v]])]]]
           [:p "No results found."]))])))

(defn stats-box []
  (let [stats (subscribe [:stats])]
    (fn []
      [:div.col-md-12 ;; TODO: do we want to visualize the 1.0 and 3.0 as a sorted line of words (order determined by some weight....)?
       (if @stats
         (let [{:keys [common
                       a-only
                       b-only
                       common-prop
                       a-unique-prop
                       b-unique-prop]}
               @stats]
           [:div
            [:p "Common: " "(50/" (count common) ") " (str/join ", " (take 50 common))]
            [:p "A only: " "(50/" (count a-only) ") " (str/join ", " (take 50 a-only))]
            [:p "B only: " "(50/" (count b-only) ") " (str/join ", " (take 50 b-only))]
            [:p "Common proportion: " common-prop]
            [:p "A unique proportion: " a-unique-prop]
            [:p "B unique proportion: " b-unique-prop]])
         [:p.text-center "Statistics = " "(waiting...)"])])))

(defn stats-small-box []
  (let [stats (subscribe [:stats])]
    (fn []
      [:div
       (gstr/format "Common: %f%, A only: %f%, B only: %f%"
                    (or (* 100 (-> @stats :common-prop)) 0)
                    (or (* 100 (-> @stats :a-unique-prop)) 0)
                    (or (* 100 (-> @stats :b-unique-prop)) 0))])))

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
     [:div.nav.navbar-nav
      [:ul.nav.navbar-nav
       [:li [:a {:href "#"} "Help"]]
       [:li [:a {:href "#" :on-click (fn [_] (dispatch [:reset-app-state]))} "Reset"]]
       [:li [:a {:href "#"} "Login"]]]]
     [:span.navbar-text.navbar-right "Signed-in as Anonymous"]]]])

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
       [:div.row.voffset [morpheme-sentences-box]]

       ;; Facet
       (if-not (and @app-state-ready? @sente-connected? (not-empty @facets))
         [:p "Loading facet-box"]
         (into [:div.row.voffset]
               (mapv
                (fn [[id _]]
                  [:div {:class (str "col-md-" (int (/ 12 (count @facets))) " hoffset")} ;;.col-md-6
                   [facet-box id]])
                @facets)))

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
