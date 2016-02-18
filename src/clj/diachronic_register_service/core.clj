(ns diachronic-register-service.core
  (:gen-class)
  (:require [diachronic-register-service.app :as app]
            [schema.core :as s]
            [reloaded.repl :refer [system init start stop go reset]]))

(defn -main
  "Start a production system, unless a system is passed as argument (as in the dev-run task)."
  [& args]
  (let [system (or (first args) #'app/prod-system)]
    (reloaded.repl/set-init! system)
    (go)))
