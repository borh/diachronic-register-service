(ns diachronic-register-service.server
  (:require [plumbing.core :refer :all]

            ring.middleware.defaults
            ;;[prone.middleware :as prone]
            [hiccup.page :refer [html5 include-css include-js]]

            [compojure.route :as route]
            [compojure.core :refer [defroutes routes GET POST]]
            ;;[gate :refer [defrouter defhandler handler]]
            ;;[bidi.ring :as bidi-ring]
            ;;[io.pedestal.http.route.definition :refer [defroutes]]
            ;;[io.pedestal.http :as http]
            ;;[io.pedestal.http.ring-middlewares :as middleware]

            [reloaded.repl :refer [system]]))

(defn login! [ring-request]
  (let [{:keys [session params]} ring-request
        {:keys [user-id]} params]
    (println "Login request: %s" params)
    {:status 200 :session (assoc session :uid user-id)}))

(defn landing-pg-handler [req]
  (html5
   {:lang "ja" :encoding "UTF-8"}
   (include-css "bootstrap.min.css")
   ;;(include-css "bootstrap-theme.min.css")
   (include-css "roboto.min.css")
   (include-css "material.min.css")
   (include-css "ripples.min.css")

   (include-css "main.css")

   (include-js "jquery-2.1.4.min.js")
   (include-js "bootstrap.min.js")
   (include-js "ripples.min.js")
   (include-js "material.min.js")
   [:script "$(document).ready(function() { $.material.init(); $.material.ripples(); $.material.input(); $.material.checkbox(); $.material.radio(); });"]

   [:meta {:content "text/html;charset=utf-8" :http-equiv "Content-Type"}]
   [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
   [:div {:id "app"}]
   [:script {:src "main.js"}]))

(defroutes my-routes
  (GET  "/"      req (landing-pg-handler req))
  ;;
  (GET  "/chsk"  req ((:ring-ajax-get-or-ws-handshake (:sente system)) req))
  (POST "/chsk"  req ((:ring-ajax-post (:sente system)) req))
  (POST "/login" req (login! req))
  ;;
  (route/resources "/react" {:root "react"})
  (route/resources "/public" {:root "public"})
  (route/resources "/") ; Static files, notably public/main.js (our cljs target)
  (route/not-found "<h1>Page not found</h1>"))

(comment
  (defn ring-ajax-get-or-ws-handshake [req] ( req))
  (defn ring-ajax-post [req] ((:ring-ajax-post (:sente system)) req))
  (defrouter my-routes
    [{:path "/"
      :get landing-pg-handler}
     {:path "/login"
      :post login!}
     {:path "/chsk"
      :get (:ring-ajax-get-or-ws-handshake (:sente system))
      :post (:ring-ajax-post (:sente system))}]
    {:resources {:path "/"
                 :root "public"}}
    {:resources {:path "/react"
                 :root "react"}}))
(comment
  (def my-routes
    (bidi-ring/make-handler
     ["/app" [[:get landing-pg-handler]]])))

(def my-ring-handler
  (let [ring-defaults-config
        (assoc-in ring.middleware.defaults/site-defaults [:security :anti-forgery]
                  {:read-token (fn [req] (-> req :params :csrf-token))})]
    (-> my-routes
        (ring.middleware.defaults/wrap-defaults ring-defaults-config))))

(comment
  (def ring-ajax-get-or-ws-handshake (fn [req] ((:ring-ajax-get-or-ws-handshake (:sente system)) req)))
  (def ring-ajax-post (fn [req] ((:ring-ajax-post (:sente system)) req)))
  (defroutes my-routes
    [[["/app" ^:interceptors [middleware/resource
                              http/html-body]
       {:get landing-pg-handler}

       "/api" ^:interceptors [middleware/params
                              middleware/keyword-params
                              middleware/nested-params
                              middleware/cookies
                              middleware/session]
       {:get identity}
       ["/chsk" {:get ring-ajax-get-or-ws-handshake
                 :post ring-ajax-post}
        "/login" {:post login!}]]]])

  ;; (def my-ring-handler
  ;;   (::http/servlet
  ;;    (http/create-servlet
  ;;     (->
  ;;      {:env                 :prod
  ;;       ::http/routes        my-routes
  ;;       ::http/resource-path "/public"
  ;;       ::http/join?         false
  ;;       ::http/router        :prefix-tree
  ;;       ::http/type          :immutant
  ;;       ::http/port          (:http-port system)}
  ;;      (http/default-interceptors)
  ;;      (http/dev-interceptors))))
  ;;
  ;;   #_(let [ring-defaults-config
  ;;           (assoc-in ring.middleware.defaults/site-defaults [:security :anti-forgery]
  ;;                     {:read-token (fn [req] (-> req :params :csrf-token))})]
  ;;       (-> my-routes
  ;;           (ring.middleware.defaults/wrap-defaults ring-defaults-config))))
  )
