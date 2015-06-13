(ns alder.comm
  (:require [chord.client :refer [ws-ch]]
            [cljs.core.async :refer [<! >! put! close! chan alts!]]
            [taoensso.timbre :refer-macros [debug]])

  (:require-macros [cljs.core.async.macros :refer [go]]))

(defonce internal-comm (chan))
(defonce current-connection (atom nil))

(defn- websocket-url []
  (let [host (.-host js/location)]
    (str "ws://" host "/alder-api-ws")))

(defn- command-loop [ws-channel]
  (swap! current-connection (fn [_] ws-channel))
  (go
    (loop [waiting-replies #queue []]
      (let [[v ch] (alts! [ws-channel internal-comm])]
        (when v
          (cond (and (= v :disconnect) (= ch internal-comm))
                (do (close! ws-channel)
                    (recur waiting-replies))

                (= ch internal-comm)
                (let [[reply-chan msg] v]
                  (>! ws-channel msg)
                  (recur (conj waiting-replies reply-chan)))

                (= ch ws-channel)
                (let [{:keys [message error]} v
                      [type name args] message]
                  (case type
                    :reply (do
                             (>! (peek waiting-replies) [name args])
                             (recur (pop waiting-replies)))
                    :command (debug "got command" message))))
          (recur waiting-replies))))))

(defn stop-socket-connection []
  (go (>! internal-comm :disconnect))
  (swap! current-connection (fn [_] nil)))

(defn start-socket-connection []
  (when @current-connection
    (stop-socket-connection))

  (go
    (let [{:keys [ws-channel error]} (<! (ws-ch (websocket-url)))]
      (if error
        (.error js/console (pr-str error))
        (command-loop ws-channel)))))

(defn create-new-patch []
  (let [reply-chan (chan)]
    (go (>! internal-comm [reply-chan [:create-new nil]]))
    reply-chan))

(defn send-serialized-graph [patch-id serialized-graph]
  (let [reply-chan (chan)]
    (go (>! internal-comm [reply-chan [:save-patch [patch-id serialized-graph]]]))
    reply-chan))

(defn get-serialized-graph [patch-id]
  (let [reply-chan (chan)]
    (go (>! internal-comm [reply-chan [:get-patch patch-id]]))
    reply-chan))

(defn create-readonly-duplicate-patch [patch-id]
  (let [reply-chan (chan)]
    (go (>! internal-comm [reply-chan [:create-readonly-duplicate patch-id]]))
    reply-chan))
