(ns diachronic-register-app.views
  (:require [clojure.string :as str]
            [goog.string :as gstr]

            [re-frame.core :refer [dispatch subscribe]]
            [reagent.core :as reagent]

            [taoensso.timbre :as timbre
             :refer-macros [log  trace  debug  info  warn  error  fatal  report
                            logf tracef debugf infof warnf errorf fatalf reportf spy]]

            [diachronic-register-app.force :as force]))

(defmulti make-selection-element (fn [coll] (:match-type (meta coll))))

(defmethod make-selection-element :OR
  [id nk k vs]
  [:div.checkbox-inline
   (for [[v v-map] (sort vs)]
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

(defn make-collapsible-panel
  [{:keys [panel-name open?]} body]
  (let [unique-id (gensym panel-name)]
    (with-meta
      [:div.panel.panel-primary {:id (str "panel" unique-id)}
       [:div.panel-heading {:role "tab" :id (str "heading" unique-id)}
        [:h3.panel-title
         [:a {:data-toggle   "collapse"
              :data-parent   (str "#panel" unique-id)
              :href          (str "#collapse" unique-id)
              :aria-expanded (if open? true false)
              :aria-controls (str "collapse" unique-id)}
          panel-name]]]
       [:div.panel-collapse.collapse;;.in <- will open on load
        {:id (str "collapse" unique-id)
         :role "tabpanel"
         :aria-labelledby (str "heading" unique-id)}
        [:div.panel-body
         body]]]
      {:key unique-id})))

(defn facet-box
  [id]
  (let [metadata (subscribe [:metadata])]
    (fn []
      (if-not @metadata
        [:p "Loading metadata..."]
        [:div.row
         ;; TODO tree zipper metadata + dynamic controls depending on datatype (year: date span, #{:OC.. ..} set support, etc.)
         (make-collapsible-panel
          {:panel-name (str/capitalize (name id))
           :open? true}
          (for [[nk kvs] (id @metadata)]
            (make-collapsible-panel
             {:panel-name (str/capitalize nk)
              :open? true}
             (for [[k vs] (sort kvs)]
               ^{:key k}
               [:div.row
                [:div.form-group;;.col-md-12
                 [:p (str/capitalize k)]
                 [:div.checkbox-inline
                  (for [[v v-map] (sort vs)]                 ;; <- this is where we want to dispatch on facet type (tree, OR, AND, ...)
                    ^{:key (str nk k (:name v-map))}
                    [:label.checkbox-inline
                     [:input
                      {:type      "checkbox"
                       :id        (str nk k (:name v-map))
                       :value     (:name v-map)
                       :checked   (:checked v-map)
                       :on-change (fn [_] (trace "Updating state:" (:checked v-map) "to" (not (:checked v-map))) (dispatch [:update-metadata [:metadata id nk k v :checked]]))}
                      (:name v-map)]])]]]))))]))))

(defn search-box
  "Lemma query box."
  []
  (let [lemma (subscribe [:lemma])
        facets (subscribe [:facets])]
    (fn []
      [:div.col-md-4.col-md-offset-4.input-group
       [:input {:class        "input form-control" :type "text"
                :placeholder  "Input any character string ..."
                :value        @lemma
                :on-change    (fn [e]
                                (let [lemma-string (.. e -target -value)]
                                  (dispatch [:update-lemma lemma-string])
                                  (dispatch [:get-morpheme-variants lemma-string])))
                :on-key-press (fn [e]
                                (when (== (.-keyCode e) 13)
                                  (let [lemma-string (.. e -target -value)] ;; FIXME do we need to get lemma here again? -> subscription value should be enough
                                    (dispatch [:update-search-state @facets :loading])
                                    (dispatch [:search-graphs lemma-string @facets]))))}]
       [:span.input-group-btn
        [:button {:class    "btn" :id "search-btn" :type "button"
                  :on-click (fn [_]
                              (dispatch [:update-search-state @facets :loading])
                              (dispatch [:search-graphs @lemma @facets]))}
         "Search"]]])))

(defn morpheme-variants-box []
  (let [morpheme-variants (subscribe [:morpheme-variants])]
    (fn []
      (if (pos? (:count @morpheme-variants))
        [:div [:ul
               (for [[morpheme freq] @morpheme-variants]
                 [:li (str morpheme "=>" freq)])]]))))


(defn tree-box-render [id]
  (trace "Rendering tree-box" id)
  [:div {:id (str "d3-tree-" (name id)) :react-key (str "d3-tree-" (name id))} [:svg]])

(defn tree-box-did-mount [id d3-tree]
  (trace "tree-box-did-mount")
  (force/make-tree-graph! id d3-tree))

(defn tree-box
  [id d3-tree]
  (reagent/create-class {:display-name "tree-box"
                         :reagent-render (tree-box-render id)
                         :component-did-mount (tree-box-did-mount id d3-tree)}))

(defn graph-box-render [id]
  (info "Rendering graph-box" id)
  [:div {:id (str "d3-node-" (name id)) :react-key (str "d3-node-" (name id))} [:svg]])

(defn graph-box-did-mount [id lemma graph]
  (info "graph-box-did-mount")
  (force/make-force-graph! id lemma graph))

(defn graph-box
  [id lemma graph]
  (reagent/create-class {:display-name "graph-box"
                         :reagent-render (graph-box-render id)
                         :component-did-mount (graph-box-did-mount id lemma graph)}))

(defn results-box
  "Renders results given lemma and metadata query."
  [id]
  (let [search-state (subscribe [:search-state])
        graph (subscribe [:graph])
        lemma (subscribe [:lemma])
        metadata (subscribe [:metadata])]
    (fn []
      (info "search-state" (-> @search-state id))
      ;;(info "graph" @graph)
      ;;(info "metadata" @metadata)
      [:div
       (case (-> @search-state id)
         :loading [:p "Searching..."]
         nil [:p "Not searching."]
         :failed [:p "Search timed out. Please try again!"]
         :full (if (not-empty (-> @metadata id))
                 [:div
                  [:div [:p (str "Results for " (str/capitalize (name id)) " with metadata "
                                 (str/join
                                  ", "
                                  (for [[_ ms] (get @metadata id)
                                        [_ vs] ms
                                        [v-name v] vs
                                        :when(:checked v)]
                                    (:name v))))]]
                  (when (not-empty (id @graph))
                    [graph-box id @lemma (id @graph)])]   ;; TODO would making the d3 node at this level help?
                 [:p "No results found."])
         :lemma (if (not-empty (-> @metadata id))
                  [:table.table
                   [:thead [:tr [:th "Lemma"] [:th "Frequency"]]]
                   [:tbody
                    (for [[k v] (-> @graph id)]          ;; TODO variable table columns
                      ^{:key (str k v)}
                      [:tr [:td k] [:td v]])]]
                  [:p "No results found."]))])))

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
      "Japanese Language Register Search"]]
    [:div.collapse.navbar-collapse {:id "navbar-top-collapse"}
     [:div.btn-group
      [:button.btn.btn-default.navbar-btn [:a {:href "#"} "Help"]]
      [:button.btn.btn-default.navbar-btn [:a {:href "#"} "Reset"]]
      [:button.btn.btn-default.navbar-btn [:a {:href "#"} "Login"]]]
     [:span.navbar-text.navbar-right "Signed-in as Anonymous"]]]])

(defn stats-box
  []
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
            [:p "B unique proportion: " b-unique-prop]
            #_(graph-area !state)])
         [:p.text-center "Statistics = " "(waiting...)"])])))

(defn stats-small-box []
  (let [stats (subscribe [:stats])]
    (fn []
      [:div
       (gstr/format "Common: %f%, A only: %f%, B only: %f%"
                    (or (* 100 (-> @stats :common-prop)) 0)
                    (or (* 100 (-> @stats :a-unique-prop)) 0)
                    (or (* 100 (-> @stats :b-unique-prop)) 0))])))

(defn app []
  (let [app-state-ready? (subscribe [:app-state-ready?])
        sente-connected? (subscribe [:sente-connected?])
        morpheme-variants (subscribe [:morpheme-variants])
        facets (subscribe [:facets])]
    (info "Loading..." "app-state:" @app-state-ready? " sente:" @sente-connected?)
    (info "facets" @facets)
    (fn []
      [:div.container-fluid
       [navbar]
       [:div.page-header
        [:h1.text-center "Japanese Language Register Search"]]
       [:div.row
        (if-not @app-state-ready?
          [:p "Loading search-box"]
          [search-box])]
       [:div.row [morpheme-variants-box]]
       #_(if (pos? (:count @morpheme-variants))
         [:div.row [tree-box "query" @morpheme-variants]])
       (if-not (and @app-state-ready? @sente-connected?)
         [:p "Loading facet-box"]
         (into [:div.row]  ;; FIXME facet-box should be dynamically created/removed
               (mapv
                (fn [id]
                  [:div {:class (str "col-md-" (int (/ 12 (count @facets))))} ;;.col-md-6
                   [facet-box id]])
                @facets)))
       (into
        [:div.row (stats-box)]  ;; FIXME facet-box should be dynamically created/removed
        (mapv
         (fn [id]
           (info "ID:----------->" id)
           [:div {:class (str "col-md-" (int (/ 12 (count @facets))))} ;;.col-md-6
            [results-box id]])
         @facets))
       #_[:div.row
        (stats-box)
        [:div.col-md-6 ;; FIXME results-box should be dynamically created/removed and should match its query facet
         [results-box :a]
         [:div#d3-node-a]]
        [:div.col-md-6
         [results-box :b]
         [:div#d3-node-b]]]
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
          [:button.btn.btn-default.navbar-btn "Details"]
          [:p.navbar-text.navbar-right "JLRS © Bor Hodošček | " [:a {:href "https://github.com/borh/jlrs"} "Source code"]]]]]])))
