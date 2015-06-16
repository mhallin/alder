(ns ^:figwheel-always alder.core
    (:require [om.core :as om :include-macros true]
              [sablono.core :as html :refer-macros [html]]
              [chord.client :refer [ws-ch]]
              [cljs.core.async :refer [<! >! put! close!]]
              [taoensso.timbre :as timbre :refer-macros [info debug]]
              [schema.core :as s :include-macros true]

              [alder.app-state :as app-state]
              [alder.selection :as selection]
              [alder.modal :as modal]

              [alder.node :as node]
              [alder.node-type :as node-type]
              [alder.node-graph :as node-graph]
              [alder.node-graph-serialize :as node-graph-serialize]
              [alder.geometry :as geometry]
              [alder.routes :as routes]
              [alder.comm :as comm]
              [alder.persist :as persist]
              [alder.views.source-export :refer [source-export-component]]
              [alder.views.inspector :refer [inspector-component]]
              [alder.views.node :refer [node-component]]
              [alder.views.prototype-node :refer [prototype-node-component]]
              [alder.views.share :refer [share-component]]
              [alder.views.modal-container :refer [modal-container-component]]
              [alder.views.navbar :refer [navbar-component]]
              [alder.views.index :refer [index-component]])

    (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(enable-console-print!)

(def palette-width 260)

(defn end-drag [app event]
  (when-let [dragging-data (:dragging @app)]
    (persist/set-ignore-state-changes! false)
    (let [mouse-x (.-clientX event)
          mouse-y (.-clientY event)
          mouse-point [mouse-x mouse-y]]
      (when-let [slot-path (:slot-path dragging-data)]
        (let [offset (:offset dragging-data)
              reverse (:reverse dragging-data)
              position (geometry/point-sub [mouse-x mouse-y] offset)]
          (when-let [hit (-> @app :node-graph
                             (node-graph/hit-test-slot position))]
            (let [from (if reverse hit slot-path)
                  to (if reverse slot-path hit)]
              (om/transact! app
                            #(update-in % [:node-graph]
                                        (fn [node-graph]
                                          (node-graph/connect-nodes node-graph
                                                                    from
                                                                    to))))))))

      (when-let [new-node (:new-node dragging-data)]
        (when (< mouse-x (- (.-innerWidth js/window) 260))
          (let [new-node-id (node-graph/next-node-id (:node-graph @app))
                new-node (:node new-node)]
            (om/transact! app
                          #(update-in % [:node-graph]
                                      (fn [node-graph]
                                        (node-graph/add-node node-graph new-node-id new-node))))
            (selection/set-selection! app #{new-node-id}))))))
  (om/transact! app #(update-in % [:dragging] (fn [_] nil))))

(defn update-drag [app event]
  (when-let [dragging-data (:dragging @app)]
    (let [x (.-clientX event)
          y (.-clientY event)]
      (when-let [slot-path (:slot-path dragging-data)]
        (om/transact! app #(assoc-in % [:dragging :current-pos] [x y])))

      (when-let [node-id (:node-id dragging-data)]
        (let [[offset-x offset-y] (:offset dragging-data)
              [node-x node-y] (node-graph/node-position (:node-graph @app)
                                                        node-id)
              new-node-x (- x offset-x)
              new-node-y (- y offset-y)
              dx (- new-node-x node-x)
              dy (- new-node-y node-y)
              node-ids (:selection @app)]
          (om/transact! app
                        #(update % :node-graph
                                 (fn [graph]
                                   (node-graph/nodes-move-by graph node-ids [dx dy]))))))

      (when-let [new-node (:new-node dragging-data)]
        (let [[offset-x offset-y] (:offset new-node)
              x (- x offset-x)
              y (- y offset-y)]
          (om/transact! app
                        #(update-in % [:dragging :new-node :node]
                                    (fn [n]
                                      (node/node-move-to n [x y]))))))

      (when-let [selection-start (:selection-start dragging-data)]
        (let [selection-end [x y]
              selection-rect (geometry/corners->rectangle selection-start
                                                          selection-end)
              selected-nodes (node-graph/nodes-in-rect (:node-graph @app)
                                                       selection-rect)]
          (om/transact! app
                        #(assoc-in % [:dragging :selection-end] selection-end))
          (om/transact! app
                        #(assoc % :selection (set (map first selected-nodes)))))))))

(defn node-start-drag [app node-id event]
  (when (zero? (.-button event))
    (.stopPropagation event)
    (persist/set-ignore-state-changes! true)
    (let [mouse-x (.-clientX event)
          mouse-y (.-clientY event)
          elem-x (.-left (.getBoundingClientRect (.-currentTarget event)))
          elem-y (.-top (.getBoundingClientRect (.-currentTarget event)))]
      (om/transact! app #(update-in % [:dragging]
                                    (fn [_] {:node-id node-id
                                             :offset [(- mouse-x elem-x)
                                                      (- mouse-y elem-y)]})))
      (update-drag app event))))

(defn- prototype-node-start-drag [app node-type-id event]
  (when (zero? (.-button event))
    (.stopPropagation event)
    (let [node-id (node-graph/next-node-id (:node-graph @app))
          mouse-x (.-clientX event)
          mouse-y (.-clientY event)
          elem-x (.-left (.getBoundingClientRect (.-currentTarget event)))
          elem-y (.-top (.getBoundingClientRect (.-currentTarget event)))]
      (om/transact! app
                    (fn [state]
                      (assoc state :dragging
                             {:new-node {:node (node/make-node (:context @app)
                                                               [0 0]
                                                               node-type-id)
                                         :offset [(- mouse-x elem-x)
                                                  (- mouse-y elem-y)]}})))
      (update-drag app event))))

(defn- start-selection-drag [app event]
  (when (zero? (.-button event))
    (let [mouse-x (.-clientX event)
          mouse-y (.-clientY event)]
      (.stopPropagation event)
      (om/transact! app
                    (fn [state]
                      (assoc state :dragging
                             {:selection-start [mouse-x mouse-y]})))
      (update-drag app event))))

(defn slot-start-drag
  ([app node-id slot-id offset-source event]
   (when (zero? (.-button event))
     (.stopPropagation event)
     (let [mouse-x (.-clientX event)
           mouse-y (.-clientY event)
           [offset-source-x offset-source-y] offset-source
           is-input (-> @app
                        :node-graph
                        (node-graph/node-by-id node-id)
                        node/node-type-inputs
                        (contains? slot-id))]
       (om/transact! app
                     #(update-in % [:dragging]
                                 (fn [_] {:slot-path [node-id slot-id]
                                          :current-pos [mouse-x mouse-y]
                                          :offset [(- mouse-x offset-source-x)
                                                   (- mouse-y offset-source-y)]
                                          :reverse is-input}))))))
  ([app node-id slot-id event]
   (let [node (-> @app :node-graph :nodes node-id)
         slot-frame (node/canvas-slot-frame node slot-id)
         slot-center (geometry/rectangle-center slot-frame)]
     (slot-start-drag app node-id slot-id slot-center event))))

(defn slot-disconnect-and-start-drag [app
                                      from-node-id from-slot-id
                                      to-node-id to-slot-id event]
  (om/transact! app
                #(update-in % [:node-graph]
                            (fn [node-graph]
                              (node-graph/disconnect-nodes node-graph
                                                           [from-node-id from-slot-id]
                                                           [to-node-id to-slot-id]))))
  (slot-start-drag app
                   from-node-id
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


(defn connection-view [[app
                        [from-node-id from-node from-slot-id]
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
                          #(slot-disconnect-and-start-drag app
                                                           from-node-id
                                                           from-slot-id
                                                           to-node-id
                                                           to-slot-id
                                                           %)))))))

(defn temporary-connection-view [[from-coord to-coord] owner]
  (reify
    om/IDisplayName
    (display-name [_] "TemporaryConnection")

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

    om/IWillMount
    (will-mount [_]
      (go-loop []
        (when-let [[event value] (<! (:selection-chan data))]
          (case event
            :clear (om/update! data :selection #{})
            :update (om/update! data :selection value))
          (recur))))

    om/IRender
    (render [_]
      (html
       [:div.graph-canvas
        {:on-mouse-down (partial start-selection-drag data)}
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
         (om/build-all connection-view
                       (map (fn [[[from-node-id from-slot-id] [to-node-id to-slot-id]]]
                              (let [from-node (-> data :node-graph :nodes from-node-id)
                                    to-node (-> data :node-graph :nodes to-node-id)]
                                [data
                                 [from-node-id from-node from-slot-id]
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

(defn palette-view [data owner]
  (letfn [(render-palette-group [{:keys [title node-types]}]
            (html
             [:div.palette__node-group
              [:h3.palette__node-group-title
               title]
              (om/build-all prototype-node-component
                            node-types
                            {:opts {:on-mouse-down (partial prototype-node-start-drag data)}
                             :key :default-title})]))]
    (reify
      om/IDisplayName
      (display-name [_] "Palette")

      om/IRender
      (render [_]
        (html [:div.palette
               [:div.palette__inner
                (map render-palette-group node-type/all-node-groups)
                ]])))))

(defn editor-component [data owner]
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
               {:on-mouse-up (partial end-drag data)
                :on-mouse-move (partial update-drag data)}
               (om/build navbar-component data)
               (om/build graph-canvas-view data)
               (om/build palette-view data)
               (when-let [new-node (-> data :dragging :new-node)]
                 (om/build node-component [nil (:node new-node) nil #{}]))
               [:div.state-debug (pr-str data)]
               [:div.save-data-debug
                (.stringify js/JSON
                            (node-graph-serialize/serialize-graph (:node-graph data)))]
               (om/build modal-container-component data)])))))

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

(defn dispatch-route [app page page-args]
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
                node-graph (node-graph-serialize/materialize-graph (:context @app)
                                                                   graph-js-obj)]
            (swap! app #(merge % {:current-page page
                                  :current-page-args page-args
                                  :node-graph node-graph}))))

        (when (= :create-new (first response))
          (let [[_ {:keys [short-id]}] response]
            (routes/replace-navigation! (routes/show-patch {:short-id short-id}))))))
    (swap! app #(merge % {:current-page page
                          :current-page-args page-args}))))

(routes/set-routing-callback! (partial dispatch-route app-state/app-state))
(routes/dispatch!)
(comm/start-socket-connection)
(persist/watch-state app-state/app-state)

(om/root root-component
         app-state/app-state
         {:target (. js/document (getElementById "app"))})

;; This ensures that Schema is always validating function
;; calls/responses when assertions are active
(assert
 (do (s/set-fn-validation! true) true))

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
