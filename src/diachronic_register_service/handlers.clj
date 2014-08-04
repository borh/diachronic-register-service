(ns diachronic-register-service.handlers
  (:require [ring.middleware.anti-forgery :as ring-anti-forgery]
            [taoensso.sente :as sente]

            [clojure.core.async :as async :refer (<! <!! >! >!! put! chan go go-loop)]
            [hiccup.core :as hiccup]
            [hiccup.page :refer [html5 include-css include-js]]
            [compojure.route :as route]
            [compojure.core :refer [defroutes routes GET POST]]
            [compojure.handler :as comp-handler]

            ;;[taoensso.timbre :as timbre]

            [diachronic-register-service.data :as data]))

;; (timbre/refer-timbre)
;; (timbre/set-level! :println)
;; (timbre/set-config! [:appenders :spit :enabled?] true)
;; (timbre/set-config! [:shared-appender-config :spit-filename] "server.log")


;; TODO move these to app!
(comment
  (def connection nil) ;; TODO dirty hack

  (let [{:keys [ch-recv send-fn ajax-post-fn ajax-get-or-ws-handshake-fn
                connected-uids]}
        (sente/make-channel-socket! {})]
    (def ring-ajax-post ajax-post-fn)
    (def ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn)
    (def ch-chsk ch-recv) ; ChannelSocket's receive channel
    (def chsk-send! send-fn) ; ChannelSocket's send API fn
    (def connected-uids connected-uids))) ; Watchable, read-only atom

                                        ; Include our cljs target


(comment
  (defonce broadcaster
    (go-loop [i 0]
      (<! (async/timeout 10000))
      (println (format "Broadcasting server>user: %s" @connected-uids))
      (doseq [uid (:any @connected-uids)]
        (chsk-send! uid
                    [:some/broadcast
                     {:what-is-this "A broadcast pushed from server"
                      :how-often    "Every 10 seconds"
                      :to-whom uid
                      :i i}]))
      (recur (inc i)))))
