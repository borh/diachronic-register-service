(ns diachronic-register-app.force
  (:require [taoensso.timbre :as timbre
             :refer-macros [log  trace  debug  info  warn  error  fatal  report
                            logf tracef debugf infof warnf errorf fatalf reportf spy]]
            cljsjs.d3))

(defn build-force-layout [width height]
  (info "build-force-layout")
  (.. js/d3
      -layout
      force
      (charge -30)
      (linkDistance 20)
      (size (array width height))))

(defn setup-force-layout [force-layout graph]
  (info "setup-force-layout")
  ;;(println "sfl:" graph)
  ;;(println "sfl:" force-layout)
  (.. force-layout
      (nodes (.-nodes graph))
      (links (.-links graph))
      start))

(defn build-svg [id width height]
  (info "Selecting" (str "#d3-node-" (name id)) (.. js/d3 (select (str "d3-node-" (name id)))))
  (.. js/d3
      (select (str "div#d3-node-" (name id)))
      (append "svg")
      (attr "width" width)
      (attr "height" height)))

(defn build-links [svg links]
  ;;(info "build-links" svg links)
  (.. svg
      (selectAll ".link")
      (data links)
      enter ;; FIXME where to remove?
      (append "line")
      (attr "class" "link")
      (attr "stroke" "grey")
      (style "stroke-width" #(.sqrt js/Math (.log js/Math (.-strength %))))))

(defn build-force-nodes [svg nodes force-layout]
  ;;(info "build-nodes" #_(.-nodes graph))
  (.. svg
      (selectAll ".node")
      (data nodes)
      enter ;; FIXME where to remove?
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

(defn ->d3-graph [lemma graph] ;; TODO move server-side
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

(defn make-force-graph! [id lemma graph] ;; FIXME width height params
  (let [graph (->d3-graph lemma graph)
        width 500
        height 500
        force-layout (build-force-layout width height)
        svg (build-svg id width height)]
    (println "Creating force graph" id)
    (println "SVG" svg)
    (setup-force-layout force-layout graph)
    (let [links (build-links svg (.-links graph))
          nodes (build-force-nodes svg (.-nodes graph) force-layout)]
      (.on force-layout "tick"
           (on-tick links nodes)))))

;; var width = 960,
;;     height = 2000;
;;
;; var tree = d3.layout.tree()
;;     .size([height, width - 160]);
;;
;; var diagonal = d3.svg.diagonal()
;;     .projection(function(d) { return [d.y, d.x]; });
;;
;; var svg = d3.select("body").append("svg")
;;     .attr("width", width)
;;     .attr("height", height)
;;   .append("g")
;;     .attr("transform", "translate(40,0)");
;;
;; d3.json("/mbostock/raw/4063550/flare.json", function(error, json) {
;;   if (error) throw error;
;;
;;   var nodes = tree.nodes(json),
;;       links = tree.links(nodes);
;;
;;   var link = svg.selectAll("path.link")
;;       .data(links)
;;     .enter().append("path")
;;       .attr("class", "link")
;;       .attr("d", diagonal);
;;
;;   var node = svg.selectAll("g.node")
;;       .data(nodes)
;;     .enter().append("g")
;;       .attr("class", "node")
;;       .attr("transform", function(d) { return "translate(" + d.y + "," + d.x + ")"; })
;;
;;   node.append("circle")
;;       .attr("r", 4.5);
;;
;;   node.append("text")
;;       .attr("dx", function(d) { return d.children ? -8 : 8; })
;;       .attr("dy", 3)
;;       .attr("text-anchor", function(d) { return d.children ? "end" : "start"; })
;;       .text(function(d) { return d.name; });
;; });
;;
;; d3.select(self.frameElement).style("height", height + "px");

(defn build-tree-nodes [svg nodes tree-layout]
  ;;(info "build-tree" nodes)
  (.. svg
      (selectAll ".node")
      (data nodes)
      enter ;; FIXME where to remove?
      (append "g")
      (attr "class" "node")
      (attr "transform" #(str "translate(" (.. % -x) "," (.. % -y) ")"))
      (append "text")
      (attr "cx" 12)
      (attr "cy" ".35em")
      (text #(.-name %))
      ;;(attr "transform" #(str "translate(" (.. % -x) "," (.. % -y) ")"))
      ;;(append "text")
      ;;(attr "dx" #(if (.-children %) -8 8))
      ;;(attr "dy" 3)
      ;;(attr "text-anchor" #(if (.-children %) "end" "start"))
      ;;(text #(.-name %))
      ))
(defn make-tree-graph! [id d3-tree] ;; FIXME width height params
  (let [data (clj->js d3-tree)
        _ (info "d3-tree" d3-tree)
        width 600
        height 400
        tree-layout (.. js/d3 -layout tree (size (array width height)))
        svg (.. js/d3
                (select (str "div#d3-tree-" (name id)))
                (append "svg")
                (attr "width" width)
                (attr "height" height))
        nodes-data (.. tree-layout (nodes data))
        links-data (.. tree-layout (links nodes-data))
        ;;_ (info "nodes-data" (count nodes-data))
        ;;_ (info "links-data" (count links-data))
        links (build-links svg links-data)
        nodes (build-tree-nodes svg nodes-data tree-layout)]
    (info "done making tree graph")))
