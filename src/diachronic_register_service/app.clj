(ns diachronic-register-service.app
  (:require [schema.core :as s]
            [schema.macros :as sm]
            [clojure.tools.namespace.repl :refer [refresh]]
            [com.stuartsierra.component :as component]
            [clojure.core.cache :as cache]
            [datomic.api :as d]
            [taoensso.sente :as sente]
            [diachronic-register-service.datomic-schema :as schema]
            [diachronic-register-service.data :as data]
            [diachronic-register-service.server :as server]))

;; Centralize logging:
;; https://github.com/uswitch/blueshift/blob/master/src/uswitch/blueshift/system.clj
;; https://github.com/uswitch/bifrost/blob/master/src/uswitch/bifrost/system.clj

(defrecord Database [datomic-uri options]
  ;; Implement the Lifecycle protocol
  component/Lifecycle

  (start [component]
    (println ";; Starting database")
    (when (:delete-database options)
      (println ";; Deleting database")
      (d/delete-database datomic-uri))
    (let [created? (d/create-database datomic-uri)
          connection (d/connect datomic-uri)]
      (when (and created? (:reload options))
        @(d/transact connection schema/schema)
        (data/load-data connection (:corpora options)))
      (-> component
          (assoc :uri (java.net.URI. datomic-uri))
          (assoc :connection connection)
          (assoc :db (d/db connection)))))

  (stop [component]
    (println ";; Stopping database")
    (dissoc component :uri :connection :db)))

(defn new-database [type host port name options]
  (map->Database {:datomic-uri (format "datomic:%s://%s%s"
                                       type
                                       (if (= type "mem")
                                         ""
                                         (str host ":" port "/"))
                                       name)
                  :options options}))

(defrecord Server [db options]
  component/Lifecycle

  (start [component]
    (println ";; Starting server")

    (server/set-connection (-> db :connection))
    (-> component
        (assoc :server (server/start-api options))
        (assoc :sente (sente/start-chsk-router!
                       server/ch-chsk
                       server/event-msg-handler*)))
    #_(let [{:keys [ch-recv send-fn ajax-post-fn
                  ajax-get-or-ws-handshake-fn connected-uids]}
          (sente/make-channel-socket! {})]
      (server/set-routes ajax-post-fn ajax-get-or-ws-handshake-fn)
      #_{:ring-ajax-post ajax-post-fn
         :ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn
         :ch-chsk ch-recv ; ChannelSocket's receive channel
         :chsk-send! send-fn ; ChannelSocket's send API fn
         :connected-uids connected-uids}))

  (stop [component]
    (println ";; Stopping server")
    ;; http-kit returns a function to stop the server, so we simply call it and return nil.
    (-> component
        (update-in [:server] (fn [srv] (when-not (nil? srv) (srv :timeout 1000))))
        (update-in [:sente] (fn [rtr] (when-not (nil? rtr) (rtr)))))))

(defn new-server [options]
  (map->Server {:options options}))

(def system-components [:db :server])

(defrecord App-System [options db server]
  component/Lifecycle

  (start [this]
    (component/start-system this system-components))
  (stop [this]
    (component/stop-system this system-components)))

(defn new-system [options]
  (map->App-System {:options options}))

(defn system [options]
  (let [{:keys [type host port name]} (:db options)]
    (map->App-System
     {:options options
      :db (new-database type host port name (:options options))
      :server (component/using
               (new-server (:server options))
               [:db])
      :app (component/using
            (new-system options)
            system-components)})))

;; # Load Database

#_(defn load-data [db options]
  )
