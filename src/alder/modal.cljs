(ns alder.modal
  (:require [cljs.core.async :refer [>!]]
            [taoensso.timbre :refer-macros [debug]]
            [schema.core :as s :include-macros true])

  (:require-macros [cljs.core.async.macros :refer [go]]))

(def ModalWindowKey (s/enum :export :share))

(s/defn show-modal-window
  ([app key :- ModalWindowKey event & _]
   (.preventDefault event)
   (show-modal-window app key))

  ([app key :- ModalWindowKey]
   (go (>! (:modal-window-chan @app) [:show key]))))

(s/defn hide-modal-window
  ([app event & _]
   (.preventDefault event)
   (hide-modal-window app))

  ([app]
   (go (>! (:modal-window-chan @app) [:hide nil]))))
