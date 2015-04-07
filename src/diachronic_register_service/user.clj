(ns diachronic-register-service.user
  (:require [com.stuartsierra.component :as component]
            [clojure.tools.namespace.repl :refer [refresh]]
            [schema.core :as s]

            [cemerick.piggieback :as piggieback]
            [weasel.repl.websocket :as weasel]

            [diachronic-register-service.app :as app]))

(defonce system nil)

(defn browser-repl []
  (piggieback/cljs-repl :repl-env (weasel/repl-env :ip "0.0.0.0" :port 9001)))

(defn init []
  (alter-var-root
   #'system
   (constantly
    (app/system ;; :config-options below
     {:db {:host #_nil "localhost"
           :port #_nil 4334
           :type #_"mem" "free"
           :name "db"}
      :server {:port 3000
               :dev true}
      :options {:reload false #_true
                :delete-database false #_true
                :out-dir "data"
                :corpora {:bccwj {:metadata-dir "/data/BCCWJ-2012-dvd1/DOC/"
                                  :corpus-dir "/data/BCCWJ-2012-dvd1/C-XML/VARIABLE/"
                                  :metadata-keys #{:corpus :audience :media :topic :gender :category :addressing :target-audience :author-year :subcorpus :basename :title :year}}
                          :taiyo {:corpus-dir "/data/taiyo-corpus/XML/"
                                  :metadata-keys #{:corpus :audience :media :topic :gender :category :author-year :subcorpus :basename :title :year}}}}}))))

(defn start []
  (alter-var-root #'system component/start))

(defn stop []
  (alter-var-root
   #'system
   (fn [s] (when s (component/stop s)))))

(defn go []
  (init)
  (start))

(defn reset []
  (stop)
  (refresh :after 'diachronic-register-service.user/go))

;; (time (s/with-fn-validation (go)))
