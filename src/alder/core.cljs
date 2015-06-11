(ns ^:figwheel-always alder.core
    (:require [om.core :as om :include-macros true]
              [sablono.core :as html :refer-macros [html]]
              [chord.client :refer [ws-ch]]
              [cljs.core.async :refer [<! >! put! close!]]
              [taoensso.timbre :as timbre]

              [alder.node :as node]
              [alder.node-type :as node-type]
              [alder.node-graph :as node-graph]
              [alder.node-graph-serialize :as node-graph-serialize]
              [alder.node-render :as node-render]
              [alder.export-render :as export-render]
              [alder.geometry :as geometry]
              [alder.routes :as routes]
              [alder.comm :as comm]
              [alder.persist :as persist])

    (:require-macros [cljs.core.async.macros :refer [go]]
                     [taoensso.timbre :refer [info debug]]))

(enable-console-print!)

(def AudioContext (or (aget js/window "AudioContext")
                      (aget js/window "webkitAudioContext")))

(defonce app-state (atom {:node-graph (node-graph/make-node-graph)
                          :context (AudioContext.)
                          :dragging nil
                          :id-counter 0
                          :trash-area-rectangle nil
                          :show-export-window false
                          :current-page :none
                          :current-page-args {}}))

(defn end-drag [event]
  (when-let [dragging-data (:dragging @app-state)]
    (persist/set-ignore-state-changes! false)
    (let [mouse-x (.-clientX event)
          mouse-y (.-clientY event)
          mouse-point [mouse-x mouse-y]]
      (when-let [slot-path (:slot-path dragging-data)]
        (let [offset (:offset dragging-data)
              reverse (:reverse dragging-data)
              position (geometry/point-sub [mouse-x mouse-y] offset)]
          (when-let [hit (-> @app-state :node-graph
                             (node-graph/hit-test-slot position))]
            (let [from (if reverse hit slot-path)
                  to (if reverse slot-path hit)]
              (swap! app-state
                     #(update-in % [:node-graph]
                                 (fn [node-graph]
                                   (node-graph/connect-nodes node-graph
                                                             from
                                                             to))))))))

      (when-let [node-id (:node-id dragging-data)]
        (when (-> @app-state :trash-area-rectangle (geometry/rectangle-hit-test mouse-point))
          (swap! app-state
                 #(update-in % [:node-graph]
                             (fn [node-graph]
                               (node-graph/remove-node node-graph node-id))))))))
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
  (when (zero? (.-button event))
    (.stopPropagation event)
    (persist/set-ignore-state-changes! true)
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
     (when (zero? (.-button event))
       (.stopPropagation event)
       (let [mouse-x (.-clientX event)
             mouse-y (.-clientY event)
             [offset-source-x offset-source-y] offset-source
             is-input (-> @app-state
                          :node-graph
                          (node-graph/node-by-id node-id)
                          node/node-type-inputs
                          (contains? slot-id))]
         (swap! app-state
                #(update-in % [:dragging]
                            (fn [_] {:slot-path [node-id slot-id]
                                     :current-pos [mouse-x mouse-y]
                                     :offset [(- mouse-x offset-source-x)
                                              (- mouse-y offset-source-y)]
                                     :reverse is-input}))))))
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
    om/IDisplayName
    (display-name [_] "Connection")

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
    om/IDisplayName
    (display-name [_] "TemporaryConnectio")

    om/IRender
    (render [_]
      (html
       (connection-line from-coord
                        to-coord
                        nil)))))

(defn- current-dragging-slot [state]
  (when-let [[node-id slot-id] (:slot-path (:dragging state))]
    (let [node (-> state :node-graph :nodes node-id)]
      [node slot-id])))

(defn graph-canvas-view [data owner]
  (reify
    om/IDisplayName
    (display-name [_] "GraphCanvas")

    om/IRender
    (render [_]
      (html
       [:div.graph-canvas
        (map (fn [[id n]]
               (om/build node-render/node-component
                         [id n (current-dragging-slot data)]
                         {:opts {:on-mouse-down node-start-drag
                              :on-slot-mouse-down slot-start-drag}
                          :react-key id}))
             (:nodes (:node-graph data)))
        (map (fn [[node-id node]]
               (om/build node-render/inspector-component
                         [(:node-graph data)
                          node-id node]
                         {:react-key (name node-id)}))
             (->> data :node-graph :nodes
                  (filter (fn [[_ node]] (node :inspector-visible)))))
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

(defn- prototype-node-start-drag [node-type-id event]
  (when (zero? (.-button event))
    (let [node-id (node-graph/next-node-id (:node-graph @app-state))]
      (swap! app-state
             (fn [state]
               (update-in state [:node-graph]
                          #(node-graph/add-node %
                                                node-id
                                                node-type-id
                                                [0 0]
                                                (:context state)))))
      (node-start-drag node-id event))))

(defn- show-export-window [event]
  (.preventDefault event)
  (swap! app-state #(assoc % :show-export-window true)))

(defn- hide-export-window [event]
  (.preventDefault event)
  (swap! app-state #(assoc % :show-export-window false)))

(defn palette-view [data owner]
  (letfn [(update-trash-area-rectangle []
            (let [trash-area (om/get-node owner "trash-area")
            left (.-offsetLeft trash-area)
            top (.-offsetTop trash-area)
            width (.-offsetWidth trash-area)
            height (.-offsetHeight trash-area)
            rectangle (geometry/Rectangle. left top width height)]
              (swap! app-state #(assoc-in % [:trash-area-rectangle] rectangle))))

          (handle-resize [_]
            (update-trash-area-rectangle))

          (render-palette-group [{:keys [title node-types]}]
            (html
             [:div.palette__node-group
              [:h3.palette__node-group-title
               title]
              (om/build-all node-render/prototype-node-component
                            node-types
                            {:opts {:on-mouse-down prototype-node-start-drag}
                             :key :default-title})]))]
    (reify
      om/IDisplayName
      (display-name [_] "Palette")

      om/IDidMount
      (did-mount [_]
        (update-trash-area-rectangle)
        (.addEventListener js/window "resize" handle-resize))

      om/IWillUnmount
      (will-unmount [_]
        (.removeEventListener js/window "resize" handle-resize))

      om/IRender
      (render [_]
        (html [:div.palette
               (map render-palette-group node-type/all-node-groups)
               [:div.palette__trash-area
                {:ref "trash-area"}]
               [:a.palette__show-export-window
                {:on-click show-export-window
                 :href "#"}
                "Export"]])))))

(defn editor-component [data owner]
  (reify
    om/IDisplayName
    (display-name [_] "Editor")

    om/IRender
    (render [_]
      (html [:div.alder-root
             {:on-mouse-up #(end-drag %)
              :on-mouse-move #(update-drag %)}
             (om/build graph-canvas-view data)
             (om/build palette-view data)
             [:div.state-debug (pr-str data)]
             [:div.save-data-debug
              (.stringify js/JSON
                          (node-graph-serialize/serialize-graph (:node-graph data)))]
             (when (:show-export-window data)
               (om/build export-render/export-component
                         (:node-graph data)
                         {:opts {:on-close hide-export-window}}))]))))

(defn index-component [data owner]
  (reify
    om/IDisplayName
    (display-name [_] "Index")

    om/IWillMount
    (will-mount [_]
      (debug "Index component creating new patch")
      (let [reply-chan (comm/create-new-patch)]
        (go
          (let [[_ short-id] (<! reply-chan)]
            (routes/replace-navigation! (routes/show-patch {:short-id short-id}))))))

    om/IRender
    (render [_]
      (html [:div.alder-index
             [:h1 "Alder DSP Editor"]
             [:p "Loading initial patch..."]]))))

(defn root-component [data owner]
  (reify
    om/IDisplayName
    (display-name [_] "Root")

    om/IRender
    (render [_]
      (case (:current-page data)
        :index (om/build index-component data)
        :show-patch (om/build editor-component data)
        :none (html [:div])))))

(defn dispatch-route [page page-args]
  (debug "Dispatching route" page page-args)
  (if (= page :show-patch)
    (go
      (let [{:keys [short-id]} page-args
            chan (comm/get-serialized-graph short-id)
            [_ serialized-graph] (<! chan)
            graph-js-obj (.parse js/JSON serialized-graph)
            node-graph (node-graph-serialize/materialize-graph (:context @app-state)
                                                               graph-js-obj)]
        (swap! app-state #(merge % {:current-page page
                                    :current-page-args page-args
                                    :node-graph node-graph}))))
    (swap! app-state #(merge % {:current-page page
                                :current-page-args page-args}))))

(routes/set-routing-callback! dispatch-route)
(routes/dispatch!)
(comm/start-socket-connection)
(persist/watch-state app-state)

(om/root root-component
         app-state
         {:target (. js/document (getElementById "app"))})

(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
)
