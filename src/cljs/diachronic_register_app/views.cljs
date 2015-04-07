(ns diachronic-register-app.views
  (:require [clojure.string :as str]
            [goog.string :as gstr]

            [re-frame.core :refer [dispatch subscribe]]
            [reagent.core :as reagent]

            [diachronic-register-app.force :as force]))

(defn facet-box
  [id]
  (let [metadata (subscribe [:metadata])]
    (fn []
      (if @metadata
        [:div.row                                           ;; TODO tree zipper metadata + dynamic controls depending on datatype (year: date span, #{:OC.. ..} set support, etc.)
         (for [[nk kvs] (id @metadata)]
           ^{:key nk}
           [:div.col-md-12
            [:h3.strong (str/capitalize nk)]
            (for [[k vs] (sort kvs)]
              ^{:key k}
              [:div.row
               [:div.col-md-12.form-group
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
                      :on-change (fn [_] (println "Updating state:" (:checked v-map) "to" (not (:checked v-map))) (dispatch [:update-metadata [:metadata id nk k v :checked]]))}
                     (:name v-map)]])]]])])]
        [:p "Loading metadata..."]))))

(defn search-box
  "Lemma query box."
  []
  (let [lemma (subscribe [:lemma])]
    (fn []
      [:div.col-md-4.col-md-offset-4.input-group
       [:input {:class        "input form-control" :type "text"
                :placeholder  "Input any character string ..."
                :value        @lemma
                :on-change    #(dispatch [:update-lemma (.. % -target -value)])
                :on-key-press #(when (== (.-keyCode %) 13)
                                (let [lemma-string (.. % -target -value)] ;; FIXME do we need to get lemma here again? -> subscription value should be enough
                                  (dispatch [:update-search-state [:a :b] :loading])
                                  (dispatch [:search-graphs lemma-string [:a :b]])))}]
       [:span.input-group-btn
        [:button {:class    "btn" :id "search-btn" :type "button"
                  :on-click (fn [_]
                              (dispatch [:update-search-state [:a :b] :loading])
                              (dispatch [:search-graphs @lemma [:a :b]]))}
         "Search"]]])))

(defn graph-box-render [id]
  (println "Rendering graph-box" id)
  [:div {:id (str "d3-node-" (name id)) :react-key (str "d3-node-" (name id))} [:svg]])

(defn graph-box-did-mount [id lemma graph]
  (println "graph-box-did-mount")
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
        lemma (subscribe [:lemma])]
    (fn []
      (println (-> @search-state id))
      [:div
       (case (-> @search-state id)
         :loading [:p "Loading..."]
         :failed [:p "Search timed out. Please try again!"]
         :full (if (not-empty (-> @graph id))
                 [:div [graph-box id @lemma (id @graph)]]   ;; TODO would making the d3 node at this level help?
                 [:p "No results found."])
         :lemma (if (not-empty (-> @graph id))
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
      [:div.col-md-12                                       ;; TODO: do we want to visualize the 1.0 and 3.0 as a sorted line of words (order determined by some weight....)?
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
        sente-connected? (subscribe [:sente-connected?])]
    (println "Loading..." "app-state:" @app-state-ready? " sente:" @sente-connected?)
    (fn []
      [:div.container-fluid
       [navbar]
       [:div.page-header
        [:h1.text-center "Japanese Language Register Search"]]
       (if-not (and @app-state-ready? @sente-connected?)
         [:p "Loading facet-box"]
         [:div.row
          [:div.col-md-6
           [:h2 "A"]
           [facet-box :a]]
          [:div.col-md-6
           [:h2 "B"]
           [facet-box :b]]]
         #_(into [:div.row]
               (mapv
                 (fn [id]
                   [:div.col-md-6
                    [:h2 (name id)]
                    [facet-box id]])
                 [:a :b])))
       [:div.row
        (if-not @app-state-ready?
          [:p "Loading search-box"]
          [search-box])]
       [:div.row
        (stats-box)
        [:div.col-md-6
         [:h2 "A"]
         [results-box :a]
         [:div#d3-node-a]]
        [:div.col-md-6
         [:h2 "B"]
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
