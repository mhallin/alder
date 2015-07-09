(ns alder.dom-util
  (:require [schema.core :as s :include-macros true]
            [taoensso.timbre :refer-macros [debug]]

            [alder.geometry :as geometry]))

(s/defn elem-origin :- geometry/Point [elem]
  [(.-offsetLeft elem) (.-offsetTop elem)])

(s/defn event-mouse-pos :- geometry/Point
  ([event]
   (let [x (.-clientX event)
         y (.-clientY event)]
     [x y]))
  ([event root-elem]
   (loop [pos [(.-pageX event) (.-pageY event)]
          elem-pos (elem-origin root-elem)]
     (geometry/point-sub pos elem-pos))))

(s/defn event-buttons :- s/Int [event]
  (.-buttons event))

(s/defn left-button? :- s/Bool [event]
  (boolean (bit-and (event-buttons event) 1)))

(s/defn element-viewport-frame :- geometry/Rectangle [element]
  (let [js-rect (.getBoundingClientRect element)
        top (.-top js-rect)
        right (.-right js-rect)
        bottom (.-bottom js-rect)
        left (.-left js-rect)]
    (geometry/Rectangle. left top (- right left) (- bottom top))))
