(ns alder.ui.components.graph-canvas
  (:require [cljs.core.async :refer [chan alts! >! close!]]
            [om.core :as om]
            [sablono.core :refer-macros [html]]

            [alder.ui.dragging :as dragging]
            [alder.dom-util :as dom-util]
            [alder.geometry :as geometry]
            [alder.node :as node]
            [alder.node-graph :as node-graph]
            [alder.selection :as selection]
            [alder.ui.components.node :refer [node-component]]
            [alder.ui.components.inspector :refer [inspector-component]]
            [alder.ui.components.connection :refer [connection-component
                                                    temporary-connection-component]])

  (:require-macros [cljs.core.async.macros :refer [go-loop go]]))

(defn- start-selection-drag [app event]
  (when (dom-util/left-button? event)
    (.stopPropagation event)
    (dragging/start-selection-drag app
                                   (dom-util/event-mouse-pos event)
                                   (:selection-chan app))))

(defn- node-start-drag [app node-id event]
  (when (dom-util/left-button? event)
    (.stopPropagation event)

    (let [mouse-pos (dom-util/event-mouse-pos event)
          elem-pos (geometry/rectangle-origin
                    (dom-util/element-viewport-frame
                     (.-currentTarget event)))
          offset (geometry/point-sub mouse-pos elem-pos)]
      (dragging/start-node-drag app node-id offset (:node-drag-chan app)))))

(defn- slot-start-drag [app node-id slot-id event]
  (.stopPropagation event)

  (let [mouse-pos (dom-util/event-mouse-pos event)
        is-input (-> @app
                     :node-graph
                     (node-graph/node-by-id node-id)
                     node/node-type-inputs
                     (contains? slot-id))]
    (dragging/start-slot-drag app node-id slot-id mouse-pos is-input (:slot-drag-chan app))))


(defn- current-dragging-slot [state slot-path]
  (when-let [[node-id slot-id] slot-path]
    (let [node (-> state :node-graph :nodes node-id)]
      [node slot-id])))

(defn graph-canvas-component [data owner]
  (reify
    om/IDisplayName
    (display-name [_] "GraphCanvas")

    om/IInitState
    (init-state [_]
      {:internal-chan (chan)})

    om/IWillMount
    (will-mount [_]
      (go-loop []
        (let [selection-chan (:selection-chan data)
              node-drag-chan (:node-drag-chan data)
              internal-chan (om/get-state owner :internal-chan)
              slot-drag-chan (:slot-drag-chan data)
              [msg ch] (alts! [internal-chan
                               selection-chan
                               node-drag-chan
                               slot-drag-chan]
                              :priority true)]
          (when (= ch selection-chan)
            (when-let [[event value] msg]
              (case event
                :clear (om/update! data :selection #{})
                :update (om/update! data :selection value)
                :set-rect (om/set-state! owner :selection-rect value)
                :clear-rect (om/set-state! owner :selection-rect nil))
              (recur)))

          (when (= ch node-drag-chan)
            (let [[event value] msg
                  selection (:selection data)]
              (case event
                :offset
                (om/transact! data
                              #(update-in % [:node-graph]
                                          (fn [node-graph]
                                            (node-graph/nodes-move-by node-graph
                                                                      (:selection %1)
                                                                      value))))))
            (recur))

          (when (= ch slot-drag-chan)
            (let [[event value] msg]
              (case event
                :update (om/set-state! owner :dragging-slot value)
                :clear (om/set-state! owner :dragging-slot nil)
                :connect
                (let [[from to] value]
                  (om/transact! data
                                #(update-in % [:node-graph]
                                            (fn [node-graph]
                                              (node-graph/connect-nodes node-graph
                                                                        from to)))))
                :disconnect
                (let [[from to] value]
                  (om/transact! data
                                #(update-in % [:node-graph]
                                            (fn [node-graph]
                                              (node-graph/disconnect-nodes node-graph
                                                                           from to))))))
              (recur))))))

    om/IWillUnmount
    (will-unmount [_]
      (let [internal-chan (om/get-state owner :internal-chan)]
        (go
          (>! internal-chan :terminate)
          (close! internal-chan))))

    om/IRenderState
    (render-state [_ state]
      (html
       [:div.graph-canvas
        {:on-mouse-down (partial start-selection-drag data)}
        (when-let [selection-rect (:selection-rect state)]
          [:div.graph-canvas__selection
           {:style (geometry/rectangle->css selection-rect)}])
        (map (fn [[id n]]
               (om/build node-component
                         [id n
                          (current-dragging-slot data
                                                 (-> state :dragging-slot :slot-path))
                          (:selection data)]
                         {:opts {:on-mouse-down (fn [node-id e]
                                                  (selection/update-selection! data node-id)
                                                  (node-start-drag data node-id e))
                                 :on-slot-mouse-down (partial slot-start-drag data)}
                          :react-key id}))
             (:nodes (:node-graph data)))
        (map (fn [[node-id node]]
               (om/build inspector-component
                         [(:node-graph data)
                          node-id node]
                         {:react-key (name node-id)}))
             (->> data :node-graph :nodes
                  (filter (fn [[_ node]] (node :inspector-visible)))))
        [:svg.graph-canvas__connections
         (om/build-all connection-component
                       (map (fn [[[from-node-id from-slot-id] [to-node-id to-slot-id]]]
                              (let [from-node (-> data :node-graph :nodes from-node-id)
                                    to-node (-> data :node-graph :nodes to-node-id)]
                                [data
                                 [from-node-id from-node from-slot-id]
                                 [to-node-id to-node to-slot-id]]))
                            (-> data :node-graph :connections)))
         (when-let [{:keys [target-pos slot-path]} (:dragging-slot state)]
           (let [[node-id slot-id] slot-path
                 node (-> data :node-graph :nodes node-id)
                 [slot slot-frame] (-> node node/node-slot-canvas-frames slot-id)
                 slot-center (geometry/rectangle-center slot-frame)]
             (om/build temporary-connection-component [slot-center target-pos])))]]))))
