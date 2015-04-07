(defproject diachronic-register-service "0.1.0-SNAPSHOT"
  :description "Diachronic and synchronic register search for modern and contemporary Japanese corpora"
  :url "https://github.com/borh/diachronic-register-service"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.7.0-alpha6"]
                 [org.clojure/clojurescript "0.0-3178" :exclusions [org.clojure/tools.reader]]
                 [org.clojure/core.match "0.3.0-alpha4" :exclusions [org.clojure/tools.analyzer.jvm]] ;; Exclusion must be present for sente to compile.
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]

                 [clj-mecab "0.4.1.2"]
                 [corpus-utils "0.1.5"]
                 [d3-compat-tree "0.0.4"]
                 [org.apache.commons/commons-compress "1.9"]
                 [org.tukaani/xz "1.5"]
                 [me.raynes/fs "1.4.6"]

                 [com.datomic/datomic-free "0.9.5153" :exclusions [joda-time org.clojure/tools.cli com.fasterxml.jackson.core/jackson-databind org.jboss.logging/jboss-logging]]
                 [io.rkn/conformity "0.3.4"]

                 [com.taoensso/encore "1.23.1"]
                 [com.taoensso/timbre "3.4.0" :exclusions [com.taoensso/encore]]
                 [com.cognitect/transit-clj  "0.8.269"]
                 [com.cognitect/transit-cljs "0.8.205"]
                 [com.taoensso/sente "1.4.1"]
                 [enlive "1.1.5"]
                 [environ "1.0.0"]

                 [com.stuartsierra/component "0.2.3"]
                 [potemkin "0.3.13"]                        ;; FIXME Override
                 [prismatic/schema "0.4.0"]
                 [prismatic/plumbing "0.4.1"]

                 [aysylu/loom "0.5.0"]
                 [org.clojure/math.combinatorics "0.1.1"]
                 [primitive-math "0.1.4"]
                 [tesser.core "1.0.0"]
                 [tesser.math "1.0.0"]

                 [ring/ring "1.3.2" :exclusions [ring/ring-jetty-adapter]]
                 [ring/ring-core "1.3.2" :exclusions [org.clojure/tools.reader]]
                 [ring/ring-devel "1.3.2"]
                 [ring/ring-json "0.3.1" :exclusions [com.fasterxml.jackson.core/jackson-core]]
                 [ring/ring-defaults "0.1.4"]               ; Incl. `ring-anti-forgery`, etc.
                 [commons-codec/commons-codec "1.10"]
                 [prone "0.8.1"]
                 [compojure "1.3.3"]
                 [org.immutant/web "2.0.0-beta2"]
                 [hiccup "1.0.5"]
                 [garden "1.2.5"]

                 ;; ClojureScript-specific
                 [datascript "0.10.0"]
                 [re-frame "0.2.0"]
                 [com.facebook/react "0.12.2.4"]
                 [cljsjs/d3 "3.5.5-2"]]

  :plugins [[lein-cljsbuild "1.0.5" :exclusions [org.clojure/clojure]]
            [lein-environ "1.0.0"]]

  :min-lein-version "2.5.0"

  :uberjar-name "diachronic-register-server.jar"

  :cljsbuild {:builds {:app {:source-paths ["src/cljs"]
                             :compiler {:output-to      "resources/public/js/out/app.js"
                                        :output-dir     "resources/public/js/out"
                                        :source-map     true    ;;"resources/public/js/app.js.map"
                                        ;;:preamble       ["react/react.min.js"]
                                        ;;:externs        ["react/externs/react.js" #_"externs/d3_externs_min.js"]
                                        :optimizations  :none #_:advanced
                                        :cache-analysis true
                                        :pretty-print   true}}}}

  :profiles {:dev {:source-paths ["env/dev/clj"]

                   :datomic {:config "resources/free-transactor-template.properties"
                             :db-uri "datomic:free://localhost:4334/db"}

                   :jvm-opts ["-server" "-Xmx1g"]

                   :dependencies [[figwheel "0.2.5" :exclusions [org.clojure/clojure]]
                                  [com.cemerick/piggieback "0.2.0"]
                                  [org.clojure/tools.nrepl "0.2.10"] ;; FIXME Override
                                  [weasel "0.6.0"]
                                  [leiningen "2.5.1" :exclusions [com.fasterxml.jackson.core/jackson-core]]]

                   :repl-options {:init-ns diachronic-register-service.user
                                  :nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}

                   :plugins [[lein-figwheel "0.2.5" :exclusions [org.clojure/clojure org.clojure/tools.reader]]]

                   :figwheel {:http-server-root "public"
                              :server-port 3449
                              :css-dirs ["resources/public/css"]
                              ;;:repl false
                              }

                   :env {:is-dev true}

                   ;;:clean-targets ["resources/public/js/"]

                   :cljsbuild {:builds
                               {:app
                                {:source-paths ["env/dev/cljs"]}}}}}

  :uberjar {:datomic {:config "resources/free-transactor-template.properties"
                      :db-uri "datomic:free://localhost:4334/db"}
            ;; FIXME below should be in ENV for chestnut config
            :jvm-opts ["-server" "-XX:+UseG1GC" "-XX:+CMSParallelRemarkEnabled" "-XX:+AggressiveOpts" "-XX:+UseFastAccessorMethods" "-XX:+UseCompressedOops" "-XX:+DoEscapeAnalysis" "-XX:+UseBiasedLocking" "-Xmx4g" "-Xms4g"]
            :source-paths ["env/prod/clj"]
            :hooks [leiningen.cljsbuild]
            :env {:production true}
            :omit-source true
            :aot :all
            :cljsbuild {:builds {:app
                                 {:source-paths ["env/prod/cljs"]
                                  :compiler
                                  {:optimizations :advanced
                                   :pretty-print false}}}}}

  :main ^{:skip-aot true} diachronic-register-service.core

  :repl-options {:init-ns diachronic-register-service.user}

  :source-paths ["src" "target/classes"]

  :clean-targets ^{:protect false} ["resources/public/js" "target/classes" "target/stale"]

  :resource-paths ["resources"]

  :target-path "target/%s"

  :uberjar-exclusions [#".*\.cljs"]

  :jvm-opts ["-server" "-XX:+UseG1GC" "-XX:+CMSParallelRemarkEnabled" "-XX:+AggressiveOpts" "-XX:+UseFastAccessorMethods" "-XX:+UseCompressedOops" "-XX:+DoEscapeAnalysis" "-XX:+UseBiasedLocking" "-Xmx2000m"])
