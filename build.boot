(set-env!
 :source-paths   #{"src/clj" "src/cljs"}
 :resource-paths #{"resources"}
 :dependencies '[[adzerk/boot-cljs "1.7.228-1" :scope "test"]
                 [adzerk/boot-cljs-repl "0.3.0" :scope "test"]
                 [adzerk/boot-reload "0.4.5" :scope "test"]
                 [environ "1.0.2"]
                 [danielsz/boot-environ "0.0.5" :scope "test"]

                 [org.clojure/clojure "1.8.0" :scope "provided"]
                 [org.clojure/clojurescript "1.7.228" :exclusions [org.clojure/tools.reader]]
                 [org.clojure/core.match "0.3.0-alpha4" :exclusions [org.clojure/tools.analyzer.jvm]] ;; Exclusion must be present for sente to compile.
                 [org.clojure/core.async "0.2.374"]

                 [com.cemerick/piggieback "0.2.1" :scope "test" :exclusions [org.clojure/clojurescript org.clojure/clojure]]
                 [weasel "0.7.0" :scope "test" :exclusions [org.clojure/clojurescript org.clojure/clojure]]
                 [org.clojure/tools.nrepl "0.2.12"]
                 [devcards "0.2.1-6"]
                 [pandeiro/boot-http "0.7.3"]

                 [clj-mecab "0.4.6"]
                 [corpus-utils "0.2.3"]
                 [d3-compat-tree "0.0.9"]
                 [org.apache.commons/commons-compress "1.10"]
                 [org.tukaani/xz "1.5"]
                 [me.raynes/fs "1.4.6"]

                 ;;[com.datomic/datomic-free "0.9.5302" :exclusions [joda-time org.clojure/tools.cli com.fasterxml.jackson.core/jackson-core org.jboss.logging/jboss-logging]]
                 [com.datomic/datomic-pro "0.9.5350" :exclusions
                  [org.slf4j/slf4j-nop
                   org.slf4j/slf4j-api
                   org.slf4j/jul-to-slf4j
                   org.slf4j/log4j-over-slf4j
                   org.slf4j/slf4j-log4j12
                   org.slf4j/jcl-over-slf4j
                   org.jboss.logging/jboss-logging

                   joda-time org.clojure/tools.cli com.fasterxml.jackson.core/jackson-core]]
                 ;;[tailrecursion/boot-datomic "0.1.0-SNAPSHOT" :scope "test"]
                 [ch.qos.logback/logback-classic "1.1.5"]
                 [datomic-schema-grapher "0.0.1"]

                 [com.taoensso/encore "2.36.1"]
                 [com.taoensso/timbre "4.3.0-RC1"]
                 [com.cognitect/transit-clj  "0.8.285"]
                 [com.cognitect/transit-cljs "0.8.237"]
                 [com.taoensso/sente "1.8.0-beta1"]

                 [org.clojure/tools.reader "1.0.0-alpha3"]
                 [org.clojure/tools.namespace "0.3.0-alpha3"]
                 [org.danielsz/system "0.2.0" :exclusions [org.clojure/tools.namespace ns-tracker]]
                 [com.stuartsierra/component "0.3.1"]
                 [prismatic/schema "1.0.5"]
                 [prismatic/plumbing "0.5.2"]

                 [aysylu/loom "0.5.4"]
                 [org.clojure/math.combinatorics "0.1.1"]
                 [primitive-math "0.1.5"] ;; TODO
                 [clj-radix "0.1.0"]
                 [com.googlecode.concurrent-trees/concurrent-trees "2.5.0"] ;; TODO
                 ;;[tesser.core "1.0.0"]
                 ;;[tesser.math "1.0.0"]

                 [ring/ring "1.4.0" :exclusions [ring/ring-jetty-adapter]]
                 [ring/ring-core "1.4.0" :exclusions [org.clojure/tools.reader]]
                 [ring/ring-devel "1.4.0"]
                 [ring/ring-json "0.4.0" :exclusions [com.fasterxml.jackson.core/jackson-core]]
                 [ring/ring-defaults "0.1.5" :exclusions [javax.servlet/servlet-api]]
                 [commons-codec/commons-codec "1.10"]
                 ;; Pedestal not compatible with Sente
                 ;;[io.pedestal/pedestal.service "0.4.0" :exclusions [com.fasterxml.jackson.core/jackson-core]]
                 ;;[io.pedestal/pedestal.service-tools "0.4.0"]
                 ;;[io.pedestal/pedestal.immutant "0.4.0" :exclusions [org.immutant/web]]
                 [compojure "1.4.0" :exclusions [org.clojure/clojure instaparse]]
                 [instaparse "1.4.1" :exclusions [org.clojure/clojure]]
                 [org.immutant/web "2.1.1"]
                 [hiccup "1.0.5"]

                 ;; ClojureScript-specific
                 ;;[datascript "0.11.5"] ;; TODO
                 [re-frame "0.7.0-alpha-2"]
                 ;;[com.facebook/react "0.12.2.4"]
                 [cljsjs/d3 "3.5.7-1"]])

(set-env! :repositories #(conj % ["my.datomic.com" "https://my.datomic.com/repo"]))

(def version "0.4.0-SNAPSHOT")
(task-options! pom {:project 'diachronic-register-service
                    :version (str version "-standalone")
                    :description "Diachronic and synchronic register search for modern and contemporary Japanese corpora"
                    :url "https://github.com/borh/diachronic-register-service"
                    :license {"Eclipse Public License" "http://www.eclipse.org/legal/epl-v10.html"}})

;; Needed for Datomic:
(boot.core/load-data-readers!)

(require
 '[adzerk.boot-cljs      :refer [cljs]]
 '[adzerk.boot-cljs-repl :refer [cljs-repl start-repl repl-env]]
 '[adzerk.boot-reload    :refer [reload]]
 '[reloaded.repl :refer [init start stop go reset]]
 '[danielsz.boot-environ :refer [environ]]
 '[system.boot :refer [system run]]
 '[diachronic-register-service.app :refer [dev-system prod-system]]
 '[pandeiro.boot-http :refer [serve]])

(task-options! repl {:init-ns 'diachronic-register-service.core})

(def config
  {:http-port 4000

   :datomic {:host #_nil "localhost"
             :port #_nil 4334
             :type #_"mem" #_"free" "dev"
             :name "db"
             :reload? false #_true
             :delete-database? false #_true}

   :out-dir "data"

   :corpora {:wikipedia {:corpus-file "/data/Wikipedia/jawiki-20160113-text.xml"
                         :metadata-keys #{:corpus :category :basename :title :year}}
             :bccwj {:metadata-dir "/data/BCCWJ-2012-dvd1/DOC/"
                     :corpus-dir "/data/BCCWJ-2012-dvd1/C-XML/VARIABLE/"
                     :metadata-keys #{:corpus :audience :media :topic :gender :category :addressing :target-audience :author-year :subcorpus :basename :title :year}}
             :taiyo {:corpus-dir "/data/taiyo-corpus/XML/"
                     :metadata-keys #{:corpus :audience :media :topic :gender :category :author-year :subcorpus :basename :title :year}}}
   :debug true})

;;(require '[tailrecursion.boot-datomic :refer [datomic]])

(deftask run-transactor
  []
  (comp (wait) (speak) #_(datomic :license-key (System/getenv "DATOMIC_LICENSE_KEY")
                             :protocol "dev"
                             :host "localhost"
                             :port "4334"
                             :memory-index-threshold "32m"
                             :memory-index-max "256m"
                             :object-cache-max "128m")))

(deftask bootstrap
  "Bootstrap the Datomic database"
  [] ;; TODO
  #_(require '[diachronic-register-service.db :as db])
  #_((resolve 'db/bootstrap!) @(resolve 'diachronic-register-service.db/uri)))

;; export TIMBRE_LEVEL=':trace'
(deftask dev
  "Run a restartable system in the REPL"
  []
  (comp
   ;;(run-transactor)
   (environ :env config)
   (watch :verbose true)
   (system :sys #'dev-system :auto-start true :hot-reload true :files ["server.clj"])
   #_(reload :asset-path "public")
   #_(cljs :source-map true)
   #_(target :dir #{"target"})
   (repl :server true)))

(deftask dev-cljs
  "Start the CLJS dev env..."
  []
  (comp
   (environ :env config)
   #_(serve :port 3010 #_port :dir "target")
   (watch :verbose true)
   (system :sys #'dev-system :auto-start true :hot-reload true :files ["app.clj" "server.clj"])
   (reload ;;:asset-path "public" ;;:open-file "vim --servername saapas --remote-silent +norm%sG%s| %s"
           ;;:on-jsload 'diachronic-register-app.core
           )
   (cljs-repl)
   (cljs :source-map true
         :optimizations :none
         :parallel-build true
         :compiler-options {:devcards true})
   (repl :server true)
   (target :dir #{"target"})))

(deftask dev-two
  ""
  []
  (comp
   (environ :env config)
   (watch)
   (system :sys #'dev-system :auto-start true :hot-reload true :files ["server.clj"])
   (reload)
   (cljs-repl)
   (cljs :optimizations :none :parallel-build true)))

(deftask dev-run
  "Run a dev system from the command line"
  []
  (comp
   (environ :env config)
   (cljs :source-map true)
   (run :main-namespace "diachronic-register-service.core" :arguments [#'dev-system])
   (wait)))

(deftask process
  "Process corpora into database from the command line"
  []
  (comp
   (environ :env (merge-with merge config
                        {:datomic {:reload? true
                                   :delete-database? true}}))
   (run :main-namespace "diachronic-register-service.core" :arguments [#'prod-system])
   (wait)))

;; export TIMBRE_LEVEL=':error'
(deftask prod-run
  "Run a production system from the command line"
  []
  (comp
   (environ :env (merge config
                        {:http-port 3000
                         :repl-port 8009
                         :debug false}))
   (cljs :optimizations :advanced)
   (run :main-namespace "diachronic-register-service.core" :arguments [#'prod-system])
   (repl :server true)
   (wait)))

;; export TIMBRE_LEVEL=':error'
(deftask build
  "Builds an uberjar of this project, including CLJS assets, that can be run with java -jar"
  []
  (comp
   (cljs :optimizations :advanced) ;; TODO will this be included in build?
   (aot :namespace '#{diachronic-register-service.core})
   (pom)
   (uber)
   (jar :main 'diachronic-register-service.core)))
