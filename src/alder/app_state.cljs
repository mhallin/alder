(ns alder.app-state
  (:require [cljs.core.async :refer [chan]]

            [alder.node-graph :refer [make-node-graph]]
            [alder.audio.aapi :refer [AudioContext]]))

(defonce app-state
  (atom
   {:context (AudioContext.)

    :node-graph (make-node-graph)
    :selection #{}

    :dragging nil
    :modal-overlay nil

    :current-page :none
    :current-page-args {}

    :selection-chan (chan)
    :modal-window-chan (chan)
    }))
