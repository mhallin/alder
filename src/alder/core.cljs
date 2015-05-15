(ns ^:figwheel-always alder.core
    (:require [om.core :as om :include-macros true]
              [sablono.core :as html :refer-macros [html]]

              [alder.node :as node]
              [alder.node-graph :as node-graph]
              [alder.node-render :as node-render]
              [alder.geometry :as geometry]))

(enable-console-print!)

(def AudioContext (or js/AudioContext js/webkitAudioContext))

(defonce app-state (atom {:node-graph (node-graph/make-node-graph)
                          :context (AudioContext.)
                          :dragging nil
                          :id-counter 0}))

(defn end-drag [event]
  (when-let [dragging-data (:dragging @app-state)]
    (let [mouse-x (.-clientX event)
          mouse-y (.-clientY event)]
      (when-let [slot-path (:slot-path dragging-data)]
        (let [offset (:offset dragging-data)
              position (geometry/point-sub [mouse-x mouse-y] offset)              ]
          (when-let [hit (-> @app-state :node-graph
                             (node-graph/hit-test-slot position))]
            (swap! app-state
                   #(update-in % [:node-graph]
                               (fn [node-graph]
                                 (node-graph/connect-nodes node-graph
                                                           slot-path
                                                           hit)))))))))
  (swap! app-state #(update-in % [:dragging] (fn [_] nil))))

(defn update-drag [event]
  (when-let [dragging-data (:dragging @app-state)]
    (let [x (.-clientX event)
          y (.-clientY event)]
      (when-let [slot-path (:slot-path dragging-data)]
        (swap! app-state #(update-in % [:dragging :current-pos]
                                     (fn [_] [x y]))))

      (when-let [node-id (:node-id dragging-data)]
        (let [[offset-x offset-y] (:offset dragging-data)
              x (- x offset-x)
              y (- y offset-y)]
          (swap! app-state
                 #(update-in % [:node-graph]
                             (fn [graph]
                               (node-graph/node-move-to graph node-id [x y])))))))))

(defn node-start-drag [node-id event]
  (when (= (.-button event) 0)
    (.stopPropagation event)
    (let [mouse-x (.-clientX event)
          mouse-y (.-clientY event)
          elem-x (.-offsetLeft (.-currentTarget event))
          elem-y (.-offsetTop (.-currentTarget event))]
      (swap! app-state #(update-in % [:dragging]
                                   (fn [_] {:node-id node-id
                                            :offset [(- mouse-x elem-x)
                                                     (- mouse-y elem-y)]})))
      (update-drag event))))

(defn slot-start-drag
  ([node-id slot-id offset-source event]
     (when (= (.-button event) 0)
       (.stopPropagation event)
       (let [mouse-x (.-clientX event)
             mouse-y (.-clientY event)
             [offset-source-x offset-source-y] offset-source
             ]
         (swap! app-state
                #(update-in % [:dragging]
                            (fn [_] {:slot-path [node-id slot-id]
                                     :current-pos [mouse-x mouse-y]
                                     :offset [(- mouse-x offset-source-x)
                                              (- mouse-y offset-source-y)]}))))))
  ([node-id slot-id event]
     (let [node (-> @app-state :node-graph :nodes node-id)
           slot-frame (node/canvas-slot-frame node slot-id)
           slot-center (geometry/rectangle-center slot-frame)]
       (slot-start-drag node-id slot-id slot-center event))))

(defn slot-disconnect-and-start-drag [from-node-id from-slot-id
                                      to-node-id to-slot-id event]
  (swap! app-state
         #(update-in % [:node-graph]
                     (fn [node-graph]
                       (node-graph/disconnect-nodes node-graph
                                                    [from-node-id from-slot-id]
                                                    [to-node-id to-slot-id]))))
  (slot-start-drag from-node-id
                   from-slot-id
                   [(- (.-clientX event) 8) (- (.-clientY event) 8)]
                   event))

(defn- connection-line [[from-x from-y] [to-x to-y] on-mouse-down]
  (let [diff-x (- to-x from-x)
        diff-y (- to-y from-y)]
    [:path.graph-canvas__connection
     {:d (str "M " (+ 1 from-x) "," (+ 1 from-y) " "
              "c "
              (/ diff-x 2) ",0 "
              (/ diff-x 2) "," diff-y " "
              diff-x "," diff-y)
      :on-mouse-down #(on-mouse-down %)}]))


(defn connection-view [[[from-node-id from-node from-slot-id]
                        [to-node-id to-node to-slot-id]]
                       owner]
  (reify
    om/IRender
    (render [_]
      (let [[_ from-slot-frame] (-> from-node
                                    node/node-slot-canvas-frames
                                    from-slot-id)

            [_ to-slot-frame] (-> to-node
                                        node/node-slot-canvas-frames
                                        to-slot-id)]
        (html
         (connection-line (geometry/rectangle-center from-slot-frame)
                          (geometry/rectangle-center to-slot-frame)
                          #(slot-disconnect-and-start-drag from-node-id
                                                           from-slot-id
                                                           to-node-id
                                                           to-slot-id
                                                           %)))))))

(defn temporary-connection-view [[from-coord to-coord] owner]
  (reify
    om/IRender
    (render [_]
      (html
       (connection-line from-coord
                        to-coord
                        nil)))))

(defn graph-canvas-view [data owner]
  (reify
    om/IRender
    (render [_]
      (html
       [:div.graph-canvas
        (om/build-all node-render/node-component
                      (:nodes (:node-graph data))
                      {:opts {:on-mouse-down node-start-drag
                              :on-slot-mouse-down slot-start-drag}})
        [:svg.graph-canvas__connections
         (om/build-all connection-view
                       (map (fn [[[from-node-id from-slot-id] [to-node-id to-slot-id]]]
                              (let [from-node (-> data :node-graph :nodes from-node-id)
                                    to-node (-> data :node-graph :nodes to-node-id)]
                                [[from-node-id from-node from-slot-id]
                                 [to-node-id to-node to-slot-id]]))
                            (-> data :node-graph :connections)))
         (when-let [slot-path (-> data :dragging :slot-path)]
           (let [current-pos (-> data :dragging :current-pos)
                 mouse-offset (-> data :dragging :offset)
                 current-pos (geometry/point-sub current-pos mouse-offset)

                 [node-id slot-id] slot-path
                 node (-> data :node-graph :nodes node-id)
                 node-origin (-> node :frame geometry/rectangle-origin)
                 [slot slot-frame] (-> node node/node-slot-canvas-frames slot-id)
                 slot-center (geometry/rectangle-center slot-frame)]
             (om/build temporary-connection-view [slot-center current-pos])))]]))))

(defn- prototype-node-start-drag [node-type event]
  (when (= (.-button event) 0)
    (let [node-id (node-graph/next-node-id)]
      (swap! app-state
             (fn [state]
               (update-in state [:node-graph]
                          #(node-graph/add-node %
                                                node-id
                                                node-type
                                                [0 0]
                                                (:context state)))))
      (node-start-drag node-id event))))

(defn palette-view [data owner]
  (reify
    om/IRender
    (render [_]
      (html [:div.palette
             (om/build-all node-render/prototype-node-component
                           node/all-node-types
                           {:opts {:on-mouse-down prototype-node-start-drag}})]))))

(defn root-component [data owner]
  (reify
    om/IRender
    (render [_]
      (html [:div.alder-root
             {:on-mouse-up #(end-drag %)
              :on-mouse-move #(update-drag %)}
             (om/build graph-canvas-view data)
             (om/build palette-view data)
             [:div.state-debug (pr-str data)]
             ]))))

(om/root root-component
         app-state
         {:target (. js/document (getElementById "app"))})


(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
) 
