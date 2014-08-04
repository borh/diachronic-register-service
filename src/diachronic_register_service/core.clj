(ns diachronic-register-service.core
  (:require [schema.core :as s]
            [schema.macros :as sm]
            [clojure.tools.namespace.repl :refer [refresh]]
            [com.stuartsierra.component :as component]
            [diachronic-register-service.app :as app]))

(defn -main [& args]
  (let [[a b c] args]
    (component/start
     (app/system :TODO))))

;; Look at https://github.com/RedBrainLabs/system-graph
