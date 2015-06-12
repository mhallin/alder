(ns alder.routes
  (:require [secretary.core :as secretary :refer-macros [defroute]]
            [cljs.core.async :refer [<!]]
            [taoensso.timbre :refer-macros [debug]]

            [alder.comm :as comm]
            [alder.node-graph-serialize :as node-graph-serialize])

  (:require-macros [cljs.core.async.macros :refer [go]]))

(defonce routing-callback (atom nil))

(defroute index "/" []
  (when-let [callback @routing-callback]
    (callback :index {})))

(defroute show-patch ["/:short-id" :short-id #"[A-Za-z0-9]{12}"] [short-id]
  (when-let [callback @routing-callback]
    (callback :show-patch {:short-id short-id})))

(defn dispatch! []
  (secretary/dispatch! (.-pathname js/location)))

(defn set-routing-callback! [callback]
  (swap! routing-callback (fn [_] callback)))

(defn on-pop-state [e]
  (dispatch!))

(defn navigate-to! [route]
  (.pushState js/history #js {} "Alder" route)
  (dispatch!))

(defn replace-navigation! [route]
  (.replaceState js/history #js {} "Alder" route)
  (dispatch!))

(set! (.-onpopstate js/window) on-pop-state)
