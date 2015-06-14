(ns alder.geometry
  (:require [schema.core :as s :include-macros true]))

(defrecord Rectangle [x y width height])

(defn valid-rectangle? [{:keys [x y width height]}]
  (and (>= width 0)
       (>= height 0)))

(def ValidRectangle
  (s/both {:x s/Num :y s/Num :width s/Num :height s/Num}
          (s/pred valid-rectangle? 'valid-rectangle?)))

(def Point
  [(s/one s/Num "x") (s/one s/Num "y")])

(def CSSRectangle
  {:top s/Str :left s/Str :width s/Str :height s/Str})

(s/defn rectangle->css :- CSSRectangle
  [rectangle :- ValidRectangle]
  (let [{:keys [x y width height]} rectangle]
    {:top (str y "px")
     :left (str x "px")
     :width (str width "px")
     :height (str height "px")}))

(s/defn rectangle->vec :- [s/Num]
  [rectangle :- ValidRectangle]
  (let [{:keys [x y width height]} rectangle]
    [x y width height]))

(s/defn corners->rectangle :- ValidRectangle
  [[x1 y1] :- Point [x2 y2] :- Point]
  (let [min-x (min x1 x2)
        min-y (min y1 y2)
        max-x (max x1 x2)
        max-y (max y1 y2)]
    (Rectangle. min-x min-y
                (- max-x min-x)
                (- max-y min-y))))

(s/defn rectangle-move-to :- ValidRectangle
  [rectangle :- ValidRectangle position :- Point]
  (let [[x y] position]
    (-> rectangle
        (assoc-in [:x] x)
        (assoc-in [:y] y))))

(s/defn rectangle-move-by :- ValidRectangle
  [rectangle :- ValidRectangle offset :- Point]
  (let [[x y] offset]
    (-> rectangle
        (update-in [:x] #(+ x %))
        (update-in [:y] #(+ y %)))))

(s/defn rectangle-center :- Point
  [rectangle :- ValidRectangle]
  (let [{:keys [x y width height]} rectangle]
    [(+ x (/ width 2))
     (+ y (/ height 2))]))


(s/defn rectangle-origin :- Point
  [rectangle :- ValidRectangle]
  (let [{:keys [x y]} rectangle]
    [x y]))

(s/defn rectangle-hit-test :- s/Bool
  [rectangle :- ValidRectangle position :- Point]
  (let [{:keys [x y width height]} rectangle
        [px py] position]
    (and (>= px x)
         (<= px (+ x width))
         (>= py y)
         (<= py (+ y height)))))

(s/defn rectangles-overlap? :- s/Bool
  [ra :- ValidRectangle rb :- ValidRectangle]
  (let [{ax1 :x ay1 :y aw :width ah :height} ra
        {bx1 :x by1 :y bw :width bh :height} rb
        ax2 (+ ax1 aw)
        ay2 (+ ay1 ah)
        bx2 (+ bx1 bw)
        by2 (+ by1 bh)]
    (and (< ax1 bx2)
         (> ax2 bx1)
         (< ay1 by2)
         (> ay2 by1))))

(s/defn point-add :- Point
  [p1 :- Point p2 :- Point]
  (let [[x1 y1] p1
        [x2 y2] p2]
    [(+ x1 x2)
     (+ y1 y2)]))


(s/defn point-sub :- Point
  [p1 :- Point p2 :- Point]
  (let [[x1 y1] p1
        [x2 y2] p2]
    [(- x1 x2)
     (- y1 y2)]))
