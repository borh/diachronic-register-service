(ns diachronic-register-service.components.immutant-web
  (:require [com.stuartsierra.component :as component]
            [immutant.web :as web]))

(defrecord WebServer [port server handler]
  component/Lifecycle
  (start [component]
    (let [server (web/run handler {:port port})]
      (assoc component :server server)))
  (stop [component]
    (when server
      (web/stop server)
      component)))

(defn new-web-server
  [port handler]
  (map->WebServer {:port port :handler handler}))
