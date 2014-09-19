(ns diachronic-register-app.core
  (:require-macros [cljs.core.match.macros :refer [match]]
                   [cljs.core.async.macros :as asyncm :refer [go go-loop]]
                   [schema.macros :refer [defschema with-fn-validation #_defrecord] :as sm]
                   [plumbing.core :refer [for-map]])
  (:require ;;[clojure.browser.repl]
            [clojure.string :as str]
            [goog.string :as gstr]
            [cljs.core.match]
            [cljs.core.async :as async :refer [<! >! put! chan]]

            ;;[strokes :refer [d3]]

            [plumbing.core :refer [map-vals]]
            [om.core :as om :include-macros true]
            ;;[om-tools.dom :as dom :include-macros true]
            [sablono.core :as html :refer-macros [html]]
            [om-tools.core :refer-macros [defcomponentk]]
            [schema.core :as s]
            [taoensso.encore :as encore]
            [taoensso.sente :as sente :refer [cb-success?]]
            [taoensso.sente.packers.transit :as sente-transit]))


(comment
  (strokes/bootstrap)

  (sm/defn gen-graph
    [id nodes links]
    (println id "nodes = " nodes " links = " links)
    (-> d3
        (.select (str "#" id))
        (.append "svg:g")
        (.attr {:class "chart"
                :width 400
                :height 400})
        (.-layout)
        (.force)
        (.nodes (clj->js nodes))
        (.links (clj->js links))
        (.size #js [400 400])
        (.start))))

(enable-console-print!)

#_(defschema GraphStats
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

  (sm/defrecord TimeSpan
      [start :- s/Int
       end :- s/Int]))

(def !app-state
  "All application state."
  (atom {:channel-state nil
         :lemma "あ"
         :stats nil
         :graph {:a {}
                 :b {}}
         :search-state {:a :done
                        :b :done}
         :metadata {:a nil
                    :b nil}}))

;; https://github.com/seancorfield/om-sente/blob/master/src/cljs/om_sente/core.cljs

(let [{:keys [chsk ch-recv send-fn state]}
      (sente/make-channel-socket! "/chsk" ; Note the same URL as before
                                  {:type :auto})]
  (def chsk chsk)
  (def ch-chsk ch-recv) ; ChannelSocket's receive channel
  (def chsk-send! send-fn) ; ChannelSocket's send API fn
  (def chsk-state state)) ; Watchable, read-only atom

(enable-console-print!)

(defn- event-handler [[id data :as ev] _]
  (println "Event: %s" ev)
  (match [id data]
         [:chsk/state {:first-open? true}]
         (do (swap! !app-state assoc :channel-state :ready)
             (println "Channel socket successfully established!"))

         [:chsk/state new-state]
         (println "Chsk state change: %s" new-state)

         [:chsk/recv payload]
         (println "Push event from server: %s" payload)

         :else
         (println "Unmatched event: %s" ev)))

(defonce chsk-router
  (sente/start-chsk-router-loop! event-handler ch-chsk))

(comment
  (defn send-text-on-enter
    "When user presses ENTER, send the value of the field to the server
and clear the field's input state."
    [e owner state]
    (when (== (.-keyCode e) 13)
      (chsk-send! [:test/echo (:text state)])
      (om/set-state! owner :text ""))))

(sm/defn tree-facet
  "Generates a tree facet showing only selected leaves and minimal path."
  [tree]
  [:ul ])

(sm/defn or-facet
  "Generates an OR facet showing only attributes."
  [tree]
  [:ul ])

(sm/defn and-facet
  "Generates an AND facet showing only selected attributes."
  [tree]
  [:ul ])

(sm/defn selected-facets :- [{s/Keyword (s/enum [(s/enum s/Str s/Num)] s/Str s/Num)}]
  [metadata :- {s/Keyword {s/Keyword {(s/enum s/Str s/Num) {:name s/Str :checked s/Bool}}}}]
  (vec
   (for [[nsk nskd] metadata
         [k kd] nskd
         [v vd] kd ;; <- this is where we want to dispatch on facet type (tree, OR, AND, ...)
         :when (:checked vd)]
     {(keyword nsk k) v})))

(sm/defn search-lemma!
  [lemma cursor id]
  ;;(println lemma)
  (om/update! cursor [:search-state id] :loading)
  (let [payload (if (not= "" lemma)
                  (into [{:word/lemma lemma}]
                        (selected-facets (-> @cursor :metadata id)))
                  (selected-facets (-> @cursor :metadata id)))]
    (chsk-send! [:query/lemma payload]
                60000
                (fn [reply]
                  ;;(println "Reply: " reply)
                  (if (= :chsk/timeout reply)
                    (om/update! cursor [:search-state id] :failed)
                    (do (om/update! cursor [:search-state id] :done)
                        (om/update! cursor [:graph id] reply)))))))

(sm/defn search-graphs!
  [lemma cursor ids]
  ;;(println lemma)
  (doseq [id ids]
    (om/update! cursor [:search-state id] :loading))
  (let [payload (mapv
                 (fn [id]
                   (if (not= "" lemma)
                     (into [{:word/lemma lemma}]
                           (selected-facets (-> @cursor :metadata id)))
                     (selected-facets (-> @cursor :metadata id))))
                 ids)]
    (chsk-send! [:query/graphs payload]
                60000
                (fn [reply]
                  ;;(println "Reply: " reply)
                  (if (= :chsk/timeout reply)
                    (doseq [id ids]
                      (om/update! cursor [:search-state id] :failed))
                    (doseq [id ids]
                      (om/update! cursor [:search-state id] :done)
                      (om/update! cursor [:graph id] (get-in reply [:data (id {:a 0 :b 1})])) ;; FIXME temporary id map
                      (om/update! cursor [:stats] (:stats reply))))))))

(sm/defn metadata-to-checkboxes :- {(s/enum "document" "paragraph") {s/Str {s/Any {:name s/Str :checked s/Bool}}}}
  [metadata :- [{s/Keyword s/Any}]]
  (->> metadata
       (group-by #(namespace (first %)))
       (map-vals (fn [xs]
                   (for-map [[nsk vs] xs]
                       (name nsk)
                     (for-map [v vs] ;; vs should be dealt with based on datatype...
                         v {:name (str v) :checked false}))))))

(defcomponentk facet-box
  "Renders facet box given metadata."
  [[:data metadata channel-state :as cursor] [:opts id]]
  (will-mount
   [_]
   (if (= :chsk/open channel-state)
     (chsk-send! [:query/all-metadata :_]
                 5000
                 (fn [reply]
                   (println reply)
                   (if (keyword? reply)
                     (println "metadata load failure, reconnecting... " reply)
                     (let [metadata-checkboxes (metadata-to-checkboxes reply)]
                       ;; FIXME Hack to save on server queries.
                       (om/update! cursor [:metadata id] metadata-checkboxes)))))
     #_(sente/chsk-reconnect! chsk)))
  (render
   [_]
   (let [data (get metadata id)]
     (html
      (if data
        [:div.row ;; TODO tree zipper metadata + dynamic controls depending on datatype (year: date span, #{:OC.. ..} set support, etc.)
         (for [[nk kvs] data]
           [:div.col-md-12
            [:h3.strong (str/capitalize nk)]
            (for [[k vs] (sort kvs)]
              [:div.row
               [:div.col-md-12.form-group
                [:p (str/capitalize k)]
                [:div.checkbox-inline
                 (for [[v v-map] (sort vs)] ;; <- this is where we want to dispatch on facet type (tree, OR, AND, ...)
                   [:label.checkbox-inline
                    [:input
                     {:type "checkbox"
                      :id (str nk k (:name v-map))
                      :value (:name v-map)
                      :checked (:checked v-map)
                      :on-change (fn [_] (om/transact! cursor [:metadata id nk k v]
                                                      (fn [m] (println m)
                                                        (update-in m [:checked] not))))}
                     (:name v-map)]])]]])])]
        (do (chsk-send! [:query/all-metadata :_]
                        15000
                        (fn [reply]
                          (if (keyword? reply)
                            (println "metadata failed" reply)
                            (let [metadata-checkboxes (metadata-to-checkboxes reply)]
                              ;; FIXME Hack to save on server queries.
                              (om/update! cursor [:metadata id] metadata-checkboxes)
                              #_(om/update! cursor [:metadata :b] metadata-checkboxes)))))
            [:p "Loading metadata..."]))))))

(defcomponentk search-box
  "Lemma query box."
  [[:data lemma :as cursor]]
  (render
   [_]
   (html
    [:div.col-md-2.col-md-offset-5.input-group
     [:input {:class "input form-control" :type "text"
              :placeholder "Input any character string ..."
              :value lemma ;; or (.. % -target -value) ???
              :on-change #(om/update! cursor :lemma (.. % -target -value))
              :on-key-press #(when (== (.-keyCode %) 13)
                               (let [lemma-string (.. % -target -value)] ;; FIXME one query!
                                 (search-graphs! lemma-string cursor [:a :b])
                                 #_(search-lemma! lemma-string cursor :a)
                                 #_(search-lemma! lemma-string cursor :b)))}]
     [:span.input-group-btn
      [:button {:class "btn" :id "search-btn" :type "button"
                :on-click (fn [_]
                            (search-graphs! lemma cursor [:a :b])
                            #_(search-lemma! lemma cursor :a)
                            #_(search-lemma! lemma cursor :b))}
       "Search"]]])))

(defcomponentk results-box
  "Renders results given lemma and metadata query."
  [[:data :as cursor] [:opts id]]
  (render
   [_]
   (html
    [:div
     (case (-> cursor :search-state id)
       :loading [:p "Loading..."]
       :failed [:p "Search timed out. Please try again!"]
       :done (if (not-empty (-> cursor :graph id))
               [:table.table
                [:thead [:tr [:th "Lemma"] [:th "Frequency"]]]
                [:tbody
                 (for [[k v] (-> cursor :graph id)] ;; TODO variable table columns
                   [:tr [:td k] [:td v]])]]
               [:p "No results found."]))])))

(defcomponentk app
  "Component that displays a text field and sends it to the server when ENTER is pressed."
  [[:data lemma graph search-state metadata stats :as cursor] ;;:- {:lemma s/Str :graph {s/Str s/Num}}
   owner]
  ;;(will-mount [_] (sente/chsk-reconnect! chsk))
  (render
   [_]
   (html
    [:div.container-fluid
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
        [:span.navbar-text.navbar-right "Signed as Anonymous"]]]]
     [:div.page-header
      [:h1.text-center "Japanese Language Register Search"]]
     [:div.row
      [:div.col-md-6
       [:h2 "A"]
       (->facet-box cursor {:opts {:id :a}})]
      [:div.col-md-6
       [:h2 "B"]
       (->facet-box cursor {:opts {:id :b}})]]
     [:div.row
      (->search-box cursor)]
     [:div.row
      [:div.col-md-12
       (if stats
         (let [{:keys [common
                       a-only
                       b-only
                       common-prop
                       a-unique-prop
                       b-unique-prop]}
               stats]
           [:div
            [:p "Common: " "(50/" (count common) ") " (str/join ", " (take 50 common))]
            [:p "A only: " "(50/" (count a-only) ") " (str/join ", " (take 50 a-only))]
            [:p "B only: " "(50/" (count b-only) ") " (str/join ", " (take 50 b-only))]
            [:p "Common proportion: " common-prop]
            [:p "A unique proportion: " a-unique-prop]
            [:p "B unique proportion: " b-unique-prop]])
         [:p.text-center "Statistics = "  "(waiting...)"])]
      [:div.col-md-6
       [:h2 "A"]
       (->results-box cursor {:opts {:id :a}})]
      [:div.col-md-6
       [:h2 "B"]
       (->results-box cursor {:opts {:id :b}})]]
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
         (gstr/format "Common: %f%, A only: %f%, B only: %f%"
                      (or (* 100 (:common-prop stats)) 0)
                      (or (* 100 (:a-unique-prop stats)) 0)
                      (or (* 100 (:b-unique-prop stats)) 0))]] ;; TODO Perhaps a good place for summary stats on subsets A and B.
       [:div.collapse.navbar-collapse {:id "navbar-bottom-collapse"}
        [:button.btn.btn-default.navbar-btn "Details"]
        [:p.navbar-text.navbar-right "JLRS © Bor Hodošček | " [:a {:href "https://github.com/borh/jlrs"} "Source code"]]]]]])))

(with-fn-validation ;; Remove for production.
  (om/root app
           !app-state
           {:target (.getElementById js/document "app")}))


;; Old

(comment
  (.addEventListener (.getElementById js/document "btn-login") "click"
                     (fn [ev]
                       (let [user-id (.-value (.getElementById js/document "input-login"))]
                         (if (str/blank? user-id)
                           (js/alert "Please enter a user-id first")
                           (do
                             (println "Logging in with user-id " user-id)

          ;;; Use any login procedure you'd like. Here we'll trigger an Ajax POST
          ;;; request that resets our server-side session. Then we ask our channel
          ;;; socket to reconnect, thereby picking up the new session.

                             (encore/ajax-lite "/login" {:method :post
                                                         :params
                                                         {:user-id (str user-id)
                                                          :csrf-token (:csrf-token @chsk-state)}}
                                               (fn [ajax-resp]
                                                 (println "Ajax login response: %s" ajax-resp)))

                             (sente/chsk-reconnect! chsk)))))))
