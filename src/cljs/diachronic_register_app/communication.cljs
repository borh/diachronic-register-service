(ns diachronic-register-app.communication
  (:require [taoensso.sente :as sente :refer [cb-success?]]
            [taoensso.sente.packers.transit :as sente-transit]
            [taoensso.timbre :as timbre
             :refer-macros [log  trace  debug  info  warn  error  fatal  report
                            logf tracef debugf infof warnf errorf fatalf reportf spy]]

            [re-frame.core :refer [dispatch]]))


(def packer
  ;;"Defines our packing (serialization) format for client<->server comms."
  ;;:edn ; Default
  (sente-transit/get-flexi-packer :edn) ; Experimental, needs Transit deps
  )

(let [{:keys [chsk ch-recv send-fn state]}
      (sente/make-channel-socket! "/chsk" ; Note the same URL as before
                                  {:type :auto :packer packer})]
  (def chsk chsk)
  (def ch-chsk ch-recv) ; ChannelSocket's receive channel
  (def chsk-send! send-fn) ; ChannelSocket's send API fn
  (def chsk-state state)) ; Watchable, read-only atom

(defmulti event-msg-handler :id) ; Dispatch on event-id

;; Wrap for logging, catching, etc.:
(defn event-msg-handler* [{:as ev-msg :keys [id ?data event]}]
  (trace "Event: " event)
  (event-msg-handler ev-msg))

(defmethod event-msg-handler :default ; Fallback
  [{:as ev-msg :keys [event]}]
  (trace "Unhandled event: " event))

(defmethod event-msg-handler :chsk/state
  [{:as ev-msg :keys [?data]}]
  (trace ?data)
  (trace (:first-open? ?data) (:open? ?data))
  (trace chsk)
  (if (or (:first-open? ?data) (:open? ?data))
    (do (dispatch [:set-sente-connection-state :ready])
        (trace "Channel socket successfully established!"))
    (do (trace "Channel socket state change: " ?data)
        (dispatch [:set-sente-connection-state nil]))))

(defmethod event-msg-handler :chsk/recv
  [{:as ev-msg :keys [?data]}]
  (trace "Push event from server: " ?data))

(def chsk-router (atom nil))
(defn stop-router! [] (when-let [stop-f @chsk-router] (stop-f)))
(defn start-router! []
  (stop-router!)
  (reset! chsk-router (sente/start-chsk-router! ch-chsk event-msg-handler*)))

(defn send! [& params]
  (apply chsk-send! params))
