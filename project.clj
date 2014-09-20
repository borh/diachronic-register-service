(defproject diachronic-register-service "0.1.0-SNAPSHOT"
  :description "Diachronic and synchronic register search for modern and contemporary Japanese corpora"
  :url "https://github.com/borh/diachronic-register-service"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0-alpha1"] ;; alpha2 problem with java.lang.NoSuchMethodError: clojure.lang.SeqIterator.<init>(Lclojure/lang/ISeq;)V for schema.core.OptionalKey.iterator(core.clj:584)

                 [com.datomic/datomic-free "0.9.4899" :exclusions [org.codehaus.plexus/plexus-utils org.apache.httpcomponents/httpclient org.clojure/tools.cli com.fasterxml.jackson.core/jackson-core]]

                 [clj-mecab "0.4.1"]
                 [corpus-utils "0.1.3"]

                 [d3-compat-tree "0.0.3"]

                 [aysylu/loom "0.5.0"]
                 [org.clojure/math.combinatorics "0.0.8"]
                 [primitive-math "0.1.4"]

                 [com.stuartsierra/component "0.2.2"]

                 [prismatic/schema "0.2.6"]
                 [prismatic/plumbing "0.3.4-SNAPSHOT"]
                 [org.clojure/core.cache "0.6.4"] ;; Some package has this as an unspecified dependency
                 [com.taoensso/timbre "3.3.1"]

                 ;; [metosin/ring-swagger "0.8.8"]
                 ;; [metosin/ring-swagger-ui "2.0.16-2"]
                 [ring/ring "1.3.1"]
                 [ring/ring-core "1.3.1"]
                 [ring/ring-devel "1.3.1"]
                 [ring/ring-json "0.3.1"]
                 [ring/ring-defaults "0.1.1"] ; Incl. `ring-anti-forgery`, etc.
                 [commons-codec/commons-codec "1.9"]
                 [prone "0.6.0"]
                 [compojure "1.1.9"]
                 [http-kit "2.1.19"]
                 [com.taoensso/sente "1.1.0"]
                 [com.cognitect/transit-clj "0.8.255"]
                 [com.cognitect/transit-cljs "0.8.188"]
                 [hiccup "1.0.5"]
                 [garden "1.2.1"]
                 [org.clojure/core.match "0.2.2"]
                 ;; TODO use dire for logging: https://gist.github.com/MichaelDrogalis/3d4fb0928b2566ddc02b#file-gistfile1-clj

                 [org.clojure/clojurescript "0.0-2311" #_:exclusions #_[com.google.guava/guava]]
                 ;;[org.clojure/core.async "0.1.338.0-5c5012-alpha"]
                 [org.clojure/core.async "0.1.338.0-5c5012-alpha"]
                 [om "0.7.3"]
                 [prismatic/om-tools "0.3.3" :exclusions [org.clojure/clojure]]
                 [sablono "0.2.22"]
                 [net.drib/strokes "0.5.1"]

                 ;;[compliment "0.1.0"] ;; FIXME
                 ]
  :plugins [[lein-cljsbuild "1.0.4-SNAPSHOT"]
            [com.cemerick/austin "0.1.4"]]
  ;; :hooks [leiningen.cljsbuild]
  :cljsbuild {:builds ; Compiled in parallel
              [{:id :main
                :source-paths ["src/cljs"]
                :compiler {:output-to "resources/public/main.js"
                           :output-dir "resources/public/"
                           :optimizations #_:none :advanced
                           :source-map "resources/public/main.js.map"
                           :pretty-print false
                           :preamble ["react/react.min.js"]
                           :externs ["react/externs/react.js"]}}]}
  :main ^{:skip-aot true} diachronic-register-service.core
  :repl-options {:init-ns diachronic-register-service.user}
  :source-paths ["src"]
  :resource-paths ["resources"]
  :target-path "target/"
  :uberjar-exclusions [#".*\.cljs"]
  :jvm-opts ["-server" "-XX:+UseG1GC" "-XX:+CMSParallelRemarkEnabled" "-XX:+AggressiveOpts" "-XX:+UseFastAccessorMethods" "-XX:+UseCompressedOops" "-XX:+DoEscapeAnalysis" "-XX:+UseBiasedLocking" "-Xmx2000m"]
  :profiles {:dev {:datomic {:config "resources/free-transactor-template.properties"
                             :db-uri "datomic:free://localhost:4334/db"}
                   :jvm-opts ["-server" "-Xmx1g"]}
             :prod {:datomic {:config "resources/free-transactor-template.properties"
                              :db-uri "datomic:free://localhost:4334/db"}
                    :jvm-opts ["-server" "-XX:+UseG1GC" "-XX:+CMSParallelRemarkEnabled" "-XX:+AggressiveOpts" "-XX:+UseFastAccessorMethods" "-XX:+UseCompressedOops" "-XX:+DoEscapeAnalysis" "-XX:+UseBiasedLocking" "-Xmx4g" "-Xms4g"]}})
