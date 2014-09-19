(ns diachronic-register-service.server
  (:require [org.httpkit.server :refer [run-server]]
            [ring.middleware.reload :as reload]
            [plumbing.core :refer :all]


            [clojure.core.match :refer [match]]
            ring.middleware.defaults
            [ring.middleware.anti-forgery :as ring-anti-forgery]
            [taoensso.sente :as sente]
            [taoensso.sente.packers.transit :as sente-transit]
            [prone.middleware :as prone]
            [clojure.core.async :as async :refer (<! <!! >! >!! put! chan go go-loop)]
            [hiccup.core :as hiccup]
            [hiccup.page :refer [html5 include-css include-js]]
            [garden.core :refer [css]]
            [garden.color :as color :refer [hsl rgb]]
            [compojure.route :as route]
            [compojure.core :refer [defroutes routes GET POST]]
            [compojure.handler :as comp-handler]

            [diachronic-register-service.data :as data]))

;; Utils

(defmacro with-timeout [millis & body]
  `(let [future# (future ~@body)]
     (try
       (.get future# ~millis java.util.concurrent.TimeUnit/MILLISECONDS)
       (catch java.util.concurrent.TimeoutException x#
         (do
           (future-cancel future#)
           nil)))))

;; Utils end

(def packer
  "Defines our packing (serialization) format for client<->server comms."
  ;; :edn ; Default
  (sente-transit/get-flexi-packer :edn) ; Experimental, needs Transit deps
  )

(let [{:keys [ch-recv send-fn ajax-post-fn ajax-get-or-ws-handshake-fn
              connected-uids]}
      (sente/make-channel-socket! {:packer packer})]
  (def ring-ajax-post   ajax-post-fn)
  (def ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn)
  (def ch-chsk          ch-recv)
  (def chsk-send!       send-fn)
  (def connected-uids connected-uids))

(declare connection)
(defn set-connection [conn]
  (alter-var-root #'connection (fn [_] conn)))

(defn login! [ring-request]
  (let [{:keys [session params]} ring-request
        {:keys [user-id]} params]
    (println "Login request: %s" params)
    {:status 200 :session (assoc session :uid user-id)}))

(defn landing-pg-handler [req]
  (html5
   {:lang "ja" :encoding "UTF-8"}
   (include-css "//netdna.bootstrapcdn.com/bootstrap/3.2.0/css/bootstrap.min.css") ;; FIXME how to specify local resources?
   ;;(include-css "main.css")
   [:style (css [:body {:background (color/darken (hsl 0 0 100) 1)
                        :padding "65px"}
                 :navbar-nav [:button [:a {:line-height "1em"}]]])]
   [:meta {:content "text/html;charset=utf-8" :http-equiv "Content-Type"}]
   [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
   [:div {:id "app"}]
   #_[:div.container-fluid
    [:div.page-header
     [:h1.text-center "Japanese Language Register Search"]]
    #_[:h2 "Login with a user-id"]
    #_[:p "The server can use this id to send events to *you* specifically."]
    #_[:p [:input#input-login {:type :text :placeholder "User-id"}]
     [:button#btn-login {:type "button"} "Secure login!"]]
    ]
   [:script {:src "main.js"}]))

;; TODO https://github.com/metosin/compojure-api when exposing a public API

(defroutes my-routes
  (GET "/" req (landing-pg-handler req))
  ;;
  (GET "/chsk" req (ring-ajax-get-or-ws-handshake req))
  (POST "/chsk" req (ring-ajax-post req))
  (POST "/login" req (login! req))
  ;;
  (route/resources "/") ; Static files, notably public/main.js (our cljs target)
  (route/not-found "<h1>Page not found</h1>"))

(def my-ring-handler
  (let [ring-defaults-config
        (assoc-in ring.middleware.defaults/site-defaults [:security :anti-forgery]
                  {:read-token (fn [req] (-> req :params :csrf-token))})]
    (-> my-routes
        (ring.middleware.defaults/wrap-defaults ring-defaults-config)
        prone/wrap-exceptions)))

#_(defroutes my-ring-handler
  (-> (routes
       (GET "/"       req (landing-pg-handler req))
       ;;
       (GET "/chsk"   req (ring-ajax-get-ws req))
       (POST "/chsk"  req (ring-ajax-post req))
       (POST "/login" req (login! req))
       ;;
       (route/resources "/") ; Static files, notably public/main.js (our cljs target)
       (route/not-found "<h1>Page not found</h1>"))

      ;; Middleware

      ;; Sente adds a :csrf-token param to Ajax requests:
      (ring-anti-forgery/wrap-anti-forgery
       {:read-token (fn [req] (-> req :params :csrf-token))})

      comp-handler/site
      prone/wrap-exceptions))

(defn start-api
  "Take resources and server options, and spin up a server with http-kit"
  [options]
  ;;(alter-var-root #'connection (fn [_] (-> options :db :connection)))
  (run-server
   (if (:dev options)
     (reload/wrap-reload my-ring-handler)
     my-ring-handler)
   options))

(defmulti event-msg-handler :id) ; Dispatch on event-id
;; Wrap for logging, catching, etc.:
(defn event-msg-handler* [{:as ev-msg :keys [id ?data event]}]
  (event-msg-handler ev-msg))

(defmethod event-msg-handler :default ; Fallback
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (let [session (:session ring-req)
        uid (:uid session)]
    (println "Unhandled event: " event)
    (when-not (:dummy-reply-fn (meta ?reply-fn))
      (?reply-fn {:umatched-event-as-echoed-from-from-server event}))))

(defmethod event-msg-handler :query/all-metadata
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (?reply-fn (doto (with-timeout 10000 (data/get-all-metadata connection)) println)))

(defmethod event-msg-handler :query/lemma
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  ;; ordering?? more specific first... (i.e. topic >> corpus etc...)
  (?reply-fn (doto (with-timeout 10000 (data/get-morpheme-graph-2 connection (doto ?data println)))
               (comp println pr-str))))

(defmethod event-msg-handler :query/graphs
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (?reply-fn (with-timeout 10000 (data/get-graphs connection ?data))))

(defmethod event-msg-handler :chsk/ws-ping
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (println "WebSocket Ping received."))

(defmethod event-msg-handler :chsk/uidport-close
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (println "Port closed. Connected uids: " @connected-uids))

(defmethod event-msg-handler :chsk/uidport-open
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (println "Port opened. Connected uids: " @connected-uids))

(defmethod event-msg-handler :chsk/send
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (println "Sending: " ?data))
