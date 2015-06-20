(ns alder.ui.components.editor
  (:require [cljs.core.async :refer [alts! >! close! chan]]
            [om.core :as om]
            [sablono.core :refer-macros [html]]
            [taoensso.timbre :refer-macros [error debug]]

            [alder.dom-util :as dom-util]
            [alder.ui.dragging :as dragging]
            [alder.ui.interaction :as interaction]
            [alder.node-graph :as node-graph]
            [alder.node-graph-serialize :as node-graph-serialize]
            [alder.selection :as selection]
            [alder.ui.components.navbar :refer [navbar-component]]
            [alder.ui.components.graph-canvas :refer [graph-canvas-component]]
            [alder.ui.components.palette :refer [palette-component]]
            [alder.ui.components.new-node :refer [new-node-component]]
            [alder.ui.components.modal-container :refer [modal-container-component]])

  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(defn- end-drag [app event]
  (dragging/mouse-up app (dom-util/event-mouse-pos event)))

(defn- update-drag [app event]
  (when (dom-util/left-button? event)
    (dragging/mouse-drag app (dom-util/event-mouse-pos event))))

(defn- editor-component [data owner]
  (letfn [(handle-key-up [e]
            (when (and (= (.-keyCode e) 46))
              (debug "Removing" (:selection data))
              (.preventDefault e)
              (om/transact! data
                            #(update-in % [:node-graph]
                                        (fn [node-graph]
                                          (node-graph/remove-nodes node-graph
                                                                   (:selection %)))))
              (selection/clear-selection! data)))]
    (reify
      om/IDisplayName
      (display-name [_] "Editor")

      om/IInitState
      (init-state [_]
        {:internal-chan (chan)})

      om/IWillMount
      (will-mount [_]
        (.addEventListener js/document
                           "keyup"
                           handle-key-up)
        (go-loop []
          (let [dragging-chan (:dragging-chan data)
                internal-chan (om/get-state owner :internal-chan)
                [msg ch] (alts! [internal-chan
                                 dragging-chan]
                                :priority true)]
            (when (= ch dragging-chan)
              (when (= (:phase msg) :start)
                (try
                  (<! (interaction/handle-dragging-sequence @data dragging-chan msg))
                  (catch js/Error e
                    (error "Caught Javascript error during drag sequence processing" e))))
              (recur)))))

      om/IWillUnmount
      (will-unmount [_]
        (.removeEventListener js/document
                              "keyup"
                              handle-key-up)
        (let [internal-chan (om/get-state owner :internal-chan)]
          (go
            (>! internal-chan :terminate)
            (close! internal-chan))))

      om/IRender
      (render [_]
        (html [:div.alder-root
               {:on-mouse-up (partial end-drag data)
                :on-mouse-move (partial update-drag data)}
               (om/build navbar-component data)
               (om/build graph-canvas-component data)
               (om/build palette-component data)
               (om/build new-node-component data)
               [:div.state-debug (pr-str data)]
               [:div.save-data-debug
                (.stringify js/JSON
                            (node-graph-serialize/serialize-graph (:node-graph data)))]
               (om/build modal-container-component data)])))))
