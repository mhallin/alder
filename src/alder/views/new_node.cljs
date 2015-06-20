(ns alder.views.new-node
  (:require [cljs.core.async :refer [>! chan close! alts!]]
            [om.core :as om]
            [sablono.core :refer-macros [html]]

            [alder.node-graph :as node-graph]
            [alder.views.node :refer [node-component]])

  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn new-node-component [data owner]
  (reify
    om/IDisplayName
    (display-name [_] "NewNode")

    om/IInitState
    (init-state [_]
      {:internal-chan (chan)})

    om/IWillMount
    (will-mount [_]
      (go
        (loop []
          (let [prototype-node-drag-chan (:prototype-node-drag-chan data)
                internal-chan (om/get-state owner :internal-chan)
                [msg ch] (alts! [internal-chan prototype-node-drag-chan]
                                :priority true)]
            (when (= ch prototype-node-drag-chan)
              (let [[event value] msg]
                (case event
                  :update (om/set-state! owner :new-node value)
                  :clear (om/set-state! owner :new-node nil)
                  :commit
                  (om/transact! data
                                (fn [app]
                                  (let [node-graph (:node-graph app)
                                        node-id (node-graph/next-node-id node-graph)]
                                    (-> app
                                        (update-in [:node-graph]
                                                   #(node-graph/add-node node-graph
                                                                         node-id
                                                                         value))
                                        (assoc-in [:selection] #{node-id}))))))
                (recur)))))))

    om/IWillUnmount
    (will-unmount [_]
      (let [internal-chan (om/get-state owner :internal-chan)]
        (go
          (>! internal-chan :terminate)
          (close! internal-chan))))

    om/IRenderState
    (render-state [_ state]
      (html
       (when-let [new-node (:new-node state)]
         (om/build node-component [nil new-node nil #{}]))))))
