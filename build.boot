(set-env!
 :source-paths   #{"src" "src/cljs"}
 :resource-paths #{"resources"}
 :dependencies '[[adzerk/boot-cljs      "0.0-3308-0" :scope "test"]
                 [adzerk/boot-reload    "0.3.1"      :scope "test"]
                 [environ "1.0.0"]
                 [danielsz/boot-environ "0.0.5" :scope "test"]

                 [org.clojure/tools.nrepl "0.2.10"]

                 [org.clojure/clojure "1.7.0"]
                 [org.clojure/clojurescript "0.0-3308" :exclusions [org.clojure/tools.reader]]
                 [org.clojure/core.match "0.3.0-alpha4" :exclusions [org.clojure/tools.analyzer.jvm]] ;; Exclusion must be present for sente to compile.
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]

                 [clj-mecab "0.4.1.3"]
                 [corpus-utils "0.1.6"]
                 [d3-compat-tree "0.0.7"]
                 [org.apache.commons/commons-compress "1.9"]
                 [org.tukaani/xz "1.5"]
                 [me.raynes/fs "1.4.6"]

                 ;;[com.datomic/datomic-free "0.9.5198" :exclusions [joda-time org.clojure/tools.cli com.fasterxml.jackson.core/jackson-core com.fasterxml.jackson.core/jackson-databind com.fasterxml.jackson.core/jackson-annotations org.jboss.logging/jboss-logging]]
                 [com.datomic/datomic-pro "0.9.5198" :exclusions
                  [org.slf4j/slf4j-api
                   org.slf4j/jul-to-slf4j
                   org.slf4j/slf4j-nop
                   org.slf4j/log4j-over-slf4j
                   org.slf4j/slf4j-log4j12
                   ;;org.slf4j/jcl-over-slf4j
                   org.jboss.logging/jboss-logging

                   joda-time org.clojure/tools.cli com.fasterxml.jackson.core/jackson-core com.fasterxml.jackson.core/jackson-databind com.fasterxml.jackson.core/jackson-annotations]]
                 ;;[tailrecursion/boot-datomic "0.1.0-SNAPSHOT" :scope "test"]

                 [com.taoensso/encore "2.1.1"]
                 [com.taoensso/timbre "4.0.2"]
                 [com.cognitect/transit-clj  "0.8.275"]
                 [com.cognitect/transit-cljs "0.8.220"]
                 [com.taoensso/sente "1.5.0"]

                 [org.clojure/tools.namespace "0.2.10"]
                 [org.danielsz/system "0.1.8" :exclusions [org.clojure/tools.namespace ns-tracker]]
                 [com.stuartsierra/component "0.2.3"]
                 [prismatic/schema "0.4.3"]
                 [prismatic/plumbing "0.4.4"]

                 [aysylu/loom "0.5.4"]
                 [org.clojure/math.combinatorics "0.1.1"]
                 [primitive-math "0.1.4"] ;; TODO
                 [com.googlecode.concurrent-trees/concurrent-trees "2.4.0"] ;; TODO
                 ;;[tesser.core "1.0.0"]
                 ;;[tesser.math "1.0.0"]

                 [ring/ring "1.4.0" :exclusions [ring/ring-jetty-adapter]]
                 [ring/ring-core "1.4.0" :exclusions [org.clojure/tools.reader]]
                 [ring/ring-devel "1.4.0"]
                 [ring/ring-json "0.3.1" :exclusions [com.fasterxml.jackson.core/jackson-core]]
                 [ring/ring-defaults "0.1.5" :exclusions [javax.servlet/servlet-api]]
                 [commons-codec/commons-codec "1.10"]
                 ;; Pedestal not compatible with Sente
                 ;;[io.pedestal/pedestal.service "0.4.0" :exclusions [com.fasterxml.jackson.core/jackson-core]]
                 ;;[io.pedestal/pedestal.service-tools "0.4.0"]
                 ;;[io.pedestal/pedestal.immutant "0.4.0" :exclusions [org.immutant/web]]
                 [compojure "1.4.0" :exclusions [org.clojure/clojure instaparse]]
                 [instaparse "1.4.1" :exclusions [org.clojure/clojure]]
                 [org.immutant/web "2.0.2"]
                 [hiccup "1.0.5"]

                 ;; ClojureScript-specific
                 ;;[datascript "0.11.5"] ;; TODO
                 [re-frame "0.4.1"]
                 ;;[com.facebook/react "0.12.2.4"]
                 [cljsjs/d3 "3.5.5-3"]])

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
 '[adzerk.boot-reload    :refer [reload]]
 '[reloaded.repl :refer [init start stop go reset]]
 '[danielsz.boot-environ :refer [environ]]
 '[system.boot :refer [system run]]
 '[diachronic-register-service.app :refer [dev-system prod-system]])

(task-options! repl {:init-ns 'diachronic-register-service.core})

(def config
  {:http-port 3000

   :datomic {:host #_nil "localhost"
             :port #_nil 4334
             :type #_"mem" #_"free" "dev"
             :name "db"
             :reload? false #_true
             :delete-database? false #_true}

   :out-dir "data"

   :corpora {:bccwj {:metadata-dir "/data/BCCWJ-2012-dvd1/DOC/"
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
   (system :sys #'dev-system :auto-start true :hot-reload true :files ["handler.clj"])
   (reload :asset-path "public")
   (cljs :source-map true)
   (repl :server true)))

(deftask dev-run
  "Run a dev system from the command line"
  []
  (comp
   (environ :env config)
   (cljs)
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
