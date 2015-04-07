(ns diachronic-register-app.force
  (:require cljsjs.d3))

(defn build-force-layout [width height]
  (println "build-force-layout")
  (.. js/d3
      -layout
      force
      (charge -30)
      (linkDistance 20)
      (size (array width height))))

(defn setup-force-layout [force-layout graph]
  (println "setup-force-layout")
  ;;(println "sfl:" graph)
  ;;(println "sfl:" force-layout)
  (.. force-layout
      (nodes (.-nodes graph))
      (links (.-links graph))
      start))

(defn build-svg [id width height]
  (println "Selecting" (str "#d3-node-" (name id)) (.. js/d3 (select (str "d3-node-" (name id)))))
  (.. js/d3
      (select (str "div#d3-node-" (name id)))
      (append "svg")
      (attr "width" width)
      (attr "height" height)))

(defn build-links [svg graph]
  (println "build-links" svg #_(.-links graph))
  (.. svg
      (selectAll ".link")
      (data (.-links graph))
      enter
      (append "line")
      (attr "class" "link")
      (attr "stroke" "grey")
      (style "stroke-width" #(.sqrt js/Math (.log js/Math (.-strength %))))))

(defn build-nodes [svg graph force-layout]
  (println "build-nodes" #_(.-nodes graph))
  (.. svg
      (selectAll ".node")
      (data (.-nodes graph))
      enter
      (append "text")
      (attr "cx" 12)
      (attr "cy" ".35em")
      (text #(.-name %))
      (call (.-drag force-layout))))

(defn on-tick [link node]
  (fn []
    (.. link
        (attr "x1" #(.. % -source -x))
        (attr "y1" #(.. % -source -y))
        (attr "x2" #(.. % -target -x))
        (attr "y2" #(.. % -target -y)))
    (.. node
        (attr "transform" #(str "translate(" (.. % -x) "," (.. % -y) ")")))))

(defn ->d3-graph [lemma graph]
  (clj->js
    (let [node-index (into {} (map-indexed (fn [i [k _]] [k i]) (into [[lemma 100]] graph)))
          nodes #_[{:name "a" :id 1 :size 1 :x 1 :y 1}
                 {:name "b" :id 2 :size 2 :x 2 :y 2}
                 {:name "c" :id 3 :size 3 :x 3 :y 3}]
          (into [{:name lemma :id (get node-index lemma) :size 100 :x 100 :y 100}]
                (for [[k v] graph]
                  {:name (name k)                      :x 120 :y 120
                   :id   (get node-index k)
                   :size v}))
          links #_[{:source "a" :target "b" :strength 2}
                 {:source "a" :target "c" :strength 1}]
          (for [[k v] graph]
            {:source   (get node-index lemma)
             :target   (get node-index k)
             :strength v})]
      (println (take 10 node-index))
      (println (filter #(< % 0) (map :size nodes)))
      (println (filter #(< % 0) (map :strength links)))
      ;; insert(n, d, x, y, x1, y1, x2, y2);
      {:nodes nodes
       :links links})))

(defn make-force-graph! [id lemma graph]
  (let [graph (->d3-graph lemma graph)
        width 500
        height 500
        force-layout (build-force-layout width height)
        svg (build-svg id width height)]
    (println "Creating force graph" id)
    (println "SVG" svg)
    (setup-force-layout force-layout graph)
    (let [links (build-links svg graph)
          ;;_ (println links)
          nodes (build-nodes svg graph force-layout)
          ;;_ (println nodes)
          ]
      (.on force-layout "tick"
           (on-tick links nodes)))))