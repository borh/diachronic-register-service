(ns diachronic-register-service.server
  (:require [org.httpkit.server :refer [run-server]]
            [ring.middleware.reload :as reload]
            [plumbing.core :refer :all]


            [clojure.core.match :refer [match]]
            [ring.middleware.anti-forgery :as ring-anti-forgery]
            [taoensso.sente :as sente]
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

(let [{:keys [ch-recv send-fn ajax-post-fn
              ajax-get-or-ws-handshake-fn] :as sente-info}
      (sente/make-channel-socket! {})]
  (def ring-ajax-post   ajax-post-fn)
  (def ring-ajax-get-ws ajax-get-or-ws-handshake-fn)
  (def ch-chsk          ch-recv)
  (def chsk-send!       send-fn))

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

(defroutes my-ring-handler
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

      comp-handler/site))

(defn start-api
  "Take resources and server options, and spin up a server with http-kit"
  [options]
  ;;(alter-var-root #'connection (fn [_] (-> options :db :connection)))
  (run-server
   (if (:dev options)
     (reload/wrap-reload my-ring-handler)
     my-ring-handler)
   options))

(defn event-msg-handler
  [{:as ev-msg :keys [ring-req event ?reply-fn]} _]
  (let [session (:session ring-req)
        uid     (:uid session)
        [id data :as ev] event]

    (println "Event: %s" ev)

    (match [id data]
           ;; TODO: Match your events here, reply when appropriate <...>
           [:query/all-metadata _]
           (?reply-fn (doto (with-timeout 10000 (data/get-all-metadata connection)) println))

           [:query/lemma _] ;; ordering?? more specific first... (i.e. topic >> corpus etc...)
           (?reply-fn (doto (with-timeout 10000 (data/get-morpheme-graph-2 connection (doto data println)))
                        (comp println pr-str)))

           [:query/graphs _] (?reply-fn (with-timeout 10000 (data/get-graphs connection data)))

           [:cursor/tx _] (println data)
           [:chsk/send _] (println data)
           [:chsk/ws-ping _] (println "WebSocket Ping received." data)

           :else
           (do (println "Unmatched event: %s" ev)
               (when-not (:dummy-reply-fn? (meta ?reply-fn))
                 (?reply-fn {:umatched-event-as-echoed-from-from-server ev}))))))
