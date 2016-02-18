(ns diachronic-register-service.messaging
  (:require [taoensso.timbre :as log]
            [plumbing.core :refer [for-map]]
            [d3-compat-tree.tree :as tree]
            [diachronic-register-service.data :as data]
            [reloaded.repl :refer [system]]))

;; # Messaging

;; ## Utils

;; FIXME there is now an official way to do this, but only for d/q queries
(defmacro with-timeout [millis & body]
  `(let [future# (future ~@body)]
     (try
       (.get future# ~millis java.util.concurrent.TimeUnit/MILLISECONDS)
       (catch java.util.concurrent.TimeoutException x#
         (do
           (future-cancel future#)
           nil)))))

;; ## Setup

(defmulti event-msg-handler :id) ; Dispatch on event-id
;; Wrap for logging, catching, etc.:
(defn event-msg-handler* [{:as ev-msg :keys [id ?data event]}]
  (event-msg-handler ev-msg))

(defmethod event-msg-handler :default ; Fallback
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (let [session (:session ring-req)
        uid (:uid session)]
    (log/info "Unhandled event: " event)
    (when-not (:dummy-reply-fn (meta ?reply-fn))
      (?reply-fn {:umatched-event-as-echoed-from-from-server event}))))

;; ## Search API

(defmethod event-msg-handler :query/all-metadata
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (?reply-fn (data/get-all-metadata (-> system :db :connection))))

(defmethod event-msg-handler :query/morpheme-variants
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (?reply-fn
   (doto
       (with-timeout 10000
         (tree/seq-to-tree
          (for [[kvs freq]
                (data/get-morpheme-variants (-> system :db :connection) ?data)]
            ;; Names in tree need to be unique:
            (let [pos (str "POS=" (:word/pos kvs))
                  lemma (str pos "/lemma=" (:word/lemma kvs))
                  orth-base (str lemma "/orth-base=" (:word/orth-base kvs))]
              {:genre [pos lemma orth-base]
               :count freq}))
          {:root-name ?data}))
     (comp println pr-str))))

(defmethod event-msg-handler :query/morpheme-sentences
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (?reply-fn
   (doto
       (if ?data
         (with-timeout 10000
           (data/get-morpheme-sentences (-> system :db :connection) ?data 10)))
     (comp println pr-str))))

(comment
  (defmethod event-msg-handler :query/lemma
    [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
    ;; ordering?? more specific first... (i.e. topic >> corpus etc...)
    ;; FIXME: http://docs.datomic.com/query.html#timeout
    (?reply-fn (doto (with-timeout 60000 (data/get-morpheme-graph-2 (-> system :db :connection) (doto ?data log/info)))
                 (comp println pr-str)))))

(defmethod event-msg-handler :query/graphs
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (log/trace ":query/graphs" ?data)
  (?reply-fn
   (when ?data
     (doto (with-timeout 10000 (data/get-graphs (-> system :db :connection) ?data))))))

(defmethod event-msg-handler :query/metadata-statistics
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (log/info ":query/metadata-statistics" ?data)
  (?reply-fn
   (when ?data
     (doto (with-timeout 10000 (data/get-metadata-statistics (-> system :db :connection) ?data))))))

;; ## Connection related logging

(defmethod event-msg-handler :chsk/ws-ping
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (log/info "WebSocket Ping received."))

(defmethod event-msg-handler :chsk/uidport-close
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (log/info "Port closed. Connected uids: " #_@connected-uids)) ;; FIXME

(defmethod event-msg-handler :chsk/uidport-open
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (log/info "Port opened. Connected uids: " #_@connected-uids)) ;; FIXME

(defmethod event-msg-handler :chsk/send
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (log/info "Sending: " ?data))
