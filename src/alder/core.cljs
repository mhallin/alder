(ns ^:figwheel-always alder.core
    (:require [om.core :as om :include-macros true]
              [sablono.core :as html :refer-macros [html]]
              [chord.client :refer [ws-ch]]
              [cljs.core.async :refer [<! >! put! close!]]
              [taoensso.timbre :as timbre :refer-macros [info debug]]

              [alder.node :as node]
              [alder.node-type :as node-type]
              [alder.node-graph :as node-graph]
              [alder.node-graph-serialize :as node-graph-serialize]
              [alder.export-render :as export-render]
              [alder.geometry :as geometry]
              [alder.routes :as routes]
              [alder.comm :as comm]
              [alder.persist :as persist]
              [alder.views.inspector :refer [inspector-component]]
              [alder.views.node :refer [node-component]]
              [alder.views.prototype-node :refer [prototype-node-component]])

    (:require-macros [cljs.core.async.macros :refer [go]]))

(enable-console-print!)

(def AudioContext (or (aget js/window "AudioContext")
                      (aget js/window "webkitAudioContext")))

(defonce app-state (atom {:node-graph (node-graph/make-node-graph)
                          :context (AudioContext.)
                          :dragging nil
                          :id-counter 0
                          :show-export-window false
                          :current-page :none
                          :current-page-args {}
                          :selection #{}}))

(def palette-width 260)

(defn- clear-selection! []
  (swap! app-state #(assoc % :selection #{})))

(defn- set-selection! [selection]
  (swap! app-state #(assoc % :selection selection)))

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

      (when-let [new-node (:new-node dragging-data)]
        (when (< mouse-x (- (.-innerWidth js/window) 260))
          (let [new-node-id (node-graph/next-node-id (:node-graph @app-state))
                new-node (:node new-node)]
            (swap! app-state
                   #(update-in % [:node-graph]
                               (fn [node-graph]
                                 (node-graph/add-node node-graph new-node-id new-node))))
            (set-selection! #{new-node-id}))))))
  (swap! app-state #(update-in % [:dragging] (fn [_] nil))))

(defn update-drag [event]
  (when-let [dragging-data (:dragging @app-state)]
    (let [x (.-clientX event)
          y (.-clientY event)]
      (when-let [slot-path (:slot-path dragging-data)]
        (swap! app-state #(assoc-in % [:dragging :current-pos] [x y])))

      (when-let [node-id (:node-id dragging-data)]
        (let [[offset-x offset-y] (:offset dragging-data)
              [node-x node-y] (node-graph/node-position (:node-graph @app-state)
                                                        node-id)
              new-node-x (- x offset-x)
              new-node-y (- y offset-y)
              dx (- new-node-x node-x)
              dy (- new-node-y node-y)
              node-ids (:selection @app-state)]
          (swap! app-state
                 #(update % :node-graph
                          (fn [graph]
                            (node-graph/nodes-move-by graph node-ids [dx dy]))))))

      (when-let [new-node (:new-node dragging-data)]
        (let [[offset-x offset-y] (:offset new-node)
              x (- x offset-x)
              y (- y offset-y)]
          (swap! app-state
                 #(update-in % [:dragging :new-node :node]
                             (fn [n]
                               (node/node-move-to n [x y]))))))

      (when-let [selection-start (:selection-start dragging-data)]
        (let [selection-end [x y]
              selection-rect (geometry/corners->rectangle selection-start
                                                          selection-end)
              selected-nodes (node-graph/nodes-in-rect (:node-graph @app-state)
                                                       selection-rect)]
          (swap! app-state
                 #(assoc-in % [:dragging :selection-end] selection-end))
          (swap! app-state
                 #(assoc % :selection (set (map first selected-nodes)))))))))

(defn node-start-drag [node-id event]
  (when (zero? (.-button event))
    (.stopPropagation event)
    (persist/set-ignore-state-changes! true)
    (let [mouse-x (.-clientX event)
          mouse-y (.-clientY event)
          elem-x (.-left (.getBoundingClientRect (.-currentTarget event)))
          elem-y (.-top (.getBoundingClientRect (.-currentTarget event)))]
      (swap! app-state #(update-in % [:dragging]
                                   (fn [_] {:node-id node-id
                                            :offset [(- mouse-x elem-x)
                                                     (- mouse-y elem-y)]})))
      (update-drag event))))

(defn- prototype-node-start-drag [node-type-id event]
  (when (zero? (.-button event))
    (.stopPropagation event)
    (let [node-id (node-graph/next-node-id (:node-graph @app-state))
          mouse-x (.-clientX event)
          mouse-y (.-clientY event)
          elem-x (.-left (.getBoundingClientRect (.-currentTarget event)))
          elem-y (.-top (.getBoundingClientRect (.-currentTarget event)))]
      (swap! app-state
             (fn [state]
               (assoc state :dragging
                      {:new-node {:node (node/make-node (:context @app-state)
                                                        [0 0]
                                                        node-type-id)
                                  :offset [(- mouse-x elem-x)
                                           (- mouse-y elem-y)]}})))
      (update-drag event))))

(defn- start-selection-drag [event]
  (when (zero? (.-button event))
    (let [mouse-x (.-clientX event)
          mouse-y (.-clientY event)]
      (.stopPropagation event)
      (swap! app-state
             (fn [state]
               (assoc state :dragging
                      {:selection-start [mouse-x mouse-y]})))
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

(defn- update-selection [node-id]
  (when-not ((:selection @app-state) node-id)
    (set-selection! #{node-id})))

(defn graph-canvas-view [data owner]
  (reify
    om/IDisplayName
    (display-name [_] "GraphCanvas")

    om/IRender
    (render [_]
      (html
       [:div.graph-canvas
        {:on-mouse-down start-selection-drag}
        (when-let [selection-start (:selection-start (:dragging data))]
          (let [selection-end (:selection-end (:dragging data))
                selection-rect (geometry/corners->rectangle selection-start
                                                            selection-end)]
            [:div.graph-canvas__selection
             {:style (geometry/rectangle->css selection-rect)}]))
        (map (fn [[id n]]
               (om/build node-component
                         [id n (current-dragging-slot data) (:selection data)]
                         {:opts {:on-mouse-down (fn [node-id e]
                                                  (update-selection node-id)
                                                  (node-start-drag node-id e))
                              :on-slot-mouse-down slot-start-drag}
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

(defn- show-export-window [event]
  (.preventDefault event)
  (swap! app-state #(assoc % :show-export-window true)))

(defn- hide-export-window [event]
  (.preventDefault event)
  (swap! app-state #(assoc % :show-export-window false)))

(defn palette-view [data owner]
  (letfn [(render-palette-group [{:keys [title node-types]}]
            (html
             [:div.palette__node-group
              [:h3.palette__node-group-title
               title]
              (om/build-all prototype-node-component
                            node-types
                            {:opts {:on-mouse-down prototype-node-start-drag}
                             :key :default-title})]))]
    (reify
      om/IDisplayName
      (display-name [_] "Palette")

      om/IRender
      (render [_]
        (html [:div.palette
               [:div.palette__inner
                (map render-palette-group node-type/all-node-groups)
                [:a.palette__show-export-window
                 {:on-click show-export-window
                  :href "#"}
                 "Export"]]])))))

(defn navbar-view [data owner]
  (letfn [(start-patch-name-editor [e]
            (.preventDefault e)
            (om/set-state! owner [:editing-patch-name] true))

          (stop-patch-name-editor []
            (when (-> data :node-graph :name empty?)
              (om/update! data [:node-graph :name] "Untitled patch"))
            (om/set-state! owner [:editing-patch-name] false))

          (on-name-editor-key-up [e]
            (when (= 13 (.-keyCode e))
              (.preventDefault e)
              (stop-patch-name-editor)))

          (update-patch-name [e]
            (om/update! data [:node-graph :name] (.-value (.-target e))))]
   (reify
     om/IDisplayName
     (display-name [_] "Navbar")

     om/IDidUpdate
     (did-update [_ _ prev-state]
       (let [was-editing (:editing-patch-name prev-state)
             is-editing (om/get-state owner :editing-patch-name)]
         (when (and is-editing (not was-editing))
           (let [editor (om/get-node owner "editor")]
             (.select editor)
             (debug "Editor" editor)))))

     om/IRenderState
     (render-state [_ state]
       (html
        [:div.navbar
         (let [name (-> data :node-graph :name)]
           (if (:editing-patch-name state)
             [:input.navbar__patch-name-editor
              {:value name
               :on-key-up on-name-editor-key-up
               :on-change update-patch-name
               :on-blur stop-patch-name-editor
               :ref "editor"}]
             [:h2.navbar__patch-name
              {:on-double-click start-patch-name-editor}
              name]))])))))

(defn editor-component [data owner]
  (letfn [(handle-key-up [e]
            (when (and (= (.-keyCode e) 46))
              (debug "Removing" (:selection @app-state))
              (.preventDefault e)
              (swap! app-state
                     #(update-in % [:node-graph]
                                 (fn [node-graph]
                                   (node-graph/remove-nodes node-graph
                                                            (:selection @app-state)))))
              (clear-selection!)))]
    (reify
      om/IDisplayName
      (display-name [_] "Editor")

      om/IDidMount
      (did-mount [_]
        (.addEventListener js/document
                           "keyup"
                           handle-key-up))

      om/IWillUnmount
      (will-unmount [_]
        (.removeEventListener js/document
                              "keyup"
                              handle-key-up))

      om/IRender
      (render [_]
        (html [:div.alder-root
               {:on-mouse-up end-drag
                :on-mouse-move update-drag}
               (om/build navbar-view data)
               (om/build graph-canvas-view data)
               (om/build palette-view data)
               (when-let [new-node (-> data :dragging :new-node)]
                 (om/build node-component [nil (:node new-node) nil #{}]))
               [:div.state-debug (pr-str data)]
               [:div.save-data-debug
                (.stringify js/JSON
                            (node-graph-serialize/serialize-graph (:node-graph data)))]
               (when (:show-export-window data)
                 (om/build export-render/export-component
                           (:node-graph data)
                           {:opts {:on-close hide-export-window}}))])))))

(defn index-component [data owner]
  (reify
    om/IDisplayName
    (display-name [_] "Index")

    om/IWillMount
    (will-mount [_]
      (debug "Index component creating new patch")
      (let [reply-chan (comm/create-new-patch)]
        (go
          (let [[_ {:keys [short-id]}] (<! reply-chan)]
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
            response (<! chan)]
        (debug "Got response from get-patch" response)
        (when (= :patch-data (first response))
          (let [[_ serialized-graph] response
                graph-js-obj (.parse js/JSON serialized-graph)
                node-graph (node-graph-serialize/materialize-graph (:context @app-state)
                                                                   graph-js-obj)]
            (swap! app-state #(merge % {:current-page page
                                        :current-page-args page-args
                                        :node-graph node-graph}))))

        (when (= :create-new (first response))
          (let [[_ {:keys [short-id]}] response]
            (routes/replace-navigation! (routes/show-patch {:short-id short-id}))))))
    (swap! app-state #(merge % {:current-page page
                                :current-page-args page-args}))))

(routes/set-routing-callback! dispatch-route)
(routes/dispatch!)
(comm/start-socket-connection)
(persist/watch-state app-state)

(om/root root-component
         app-state
         {:target (. js/document (getElementById "app"))})

(comment

  ;; Eval this in a REPL to disable logging for specific modules, or
  ;; change the logging configuration in any other way.
  (timbre/merge-config! {:ns-blacklist ["alder.persist"]})

)

(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
)
