(ns diachronic-register-service.user
  (:require [com.stuartsierra.component :as component]
            [clojure.tools.namespace.repl :refer [refresh]]
            [schema.core :as s]
            [datomic.api :as d]
            [diachronic-register-service.app :as app]))

(use 'io.aviso.exception)

(defmacro e! [body]
  `(try ~body
        (catch Exception e# (io.aviso.exception/write-exception e#))))

(defonce system nil)

(defn init []
  (alter-var-root
   #'system
   (constantly
    (app/system ;; :config-options below
     {:db {:host #_nil "localhost"
           :port #_nil 4334
           :type #_"mem" "free"
           :name "db"}
      :server {:port 3001
               ;;:join? false
               :thread 6
               ;;:dev true
               }
      :options {:reload false ;;true
                :delete-database false ;;true
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

;; (time (s/with-fn-validation (e! (go))))
