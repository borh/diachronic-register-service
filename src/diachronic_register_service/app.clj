(ns diachronic-register-service.app
  (:require [schema.core :as s]

            [com.stuartsierra.component :as component]
            [system.core :refer [defsystem]]
            [environ.core :refer [env]]

            [clojure.core.cache :as cache]
            [datomic.api :as d]
            [immutant.web :as web]

            [taoensso.sente.packers.transit :as sente-transit]
            [system.components.sente :refer [new-channel-sockets]]
            [diachronic-register-service.messaging :as messaging]
            [taoensso.sente.server-adapters.immutant :refer [sente-web-server-adapter]]

            [diachronic-register-service.components.immutant-web :refer [new-web-server]]

            [diachronic-register-service.datomic-schema :as schema]
            [diachronic-register-service.data :as data]
            [diachronic-register-service.server :as server]))

;; Centralize logging:
;; https://github.com/uswitch/blueshift/blob/master/src/uswitch/blueshift/system.clj
;; https://github.com/uswitch/bifrost/blob/master/src/uswitch/bifrost/system.clj

(defrecord Database [datomic-uri corpora delete-database? reload?]
  ;; Implement the Lifecycle protocol
  component/Lifecycle

  (start [component]
    (println datomic-uri corpora delete-database? reload?)
    (println ";; Starting database at" datomic-uri)
    #_(when delete-database?
      (println ";; Deleting database")
      (try (d/delete-database datomic-uri)
           (catch Exception e (println ";; Could not delete database:" e))))
    (let [created? (d/create-database datomic-uri)
          connection (d/connect datomic-uri)]
      (println "created?" created?)
      (when reload?
        (println ";; Recreating database")
        @(d/transact connection schema/schema)
        (data/load-data connection corpora))
      (-> component
          (assoc :uri (java.net.URI. datomic-uri))
          (assoc :connection connection)
          (assoc :db (d/db connection)))))

  (stop [component]
    (println ";; Stopping database")
    (dissoc component :uri :connection :db)))

(defn new-database [{:keys [type host port name delete-database? reload?]} corpora]
  (map->Database {:datomic-uri (format "datomic:%s://%s%s"
                                       type
                                       (if (= type "mem")
                                         ""
                                         (str host ":" port "/"))
                                       name)
                  :corpora corpora
                  :delete-database? delete-database?
                  :reload? reload?}))

(defsystem dev-system
  [:sente (new-channel-sockets messaging/event-msg-handler* sente-web-server-adapter {:packer (sente-transit/get-flexi-packer :edn)})
   :db (new-database (env :datomic) (env :corpora))
   :server (new-web-server (env :http-port) server/my-ring-handler)])

(def prod-system dev-system)

;; # Load Database

#_(defn load-data [db options]
  )
