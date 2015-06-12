(ns alder.geometry)

(defrecord Rectangle
    [x y width height])

(defn rectangle->css [rectangle]
  (let [{:keys [x y width height]} rectangle]
    {:top (str y "px")
     :left (str x "px")
     :width (str width "px")
     :height (str height "px")}))

(defn rectangle->vec [rectangle]
  (let [{:keys [x y width height]} rectangle]
    [x y width height]))

(defn corners->rectangle [[x1 y1] [x2 y2]]
  (let [min-x (min x1 x2)
        min-y (min y1 y2)
        max-x (max x1 x2)
        max-y (max y1 y2)]
    (Rectangle. min-x min-y
                (- max-x min-x)
                (- max-y min-y))))

(defn rectangle-move-to [rectangle position]
  (let [[x y] position]
    (-> rectangle
        (assoc-in [:x] x)
        (assoc-in [:y] y))))

(defn rectangle-move-by [rectangle offset]
  (let [[x y] offset]
    (-> rectangle
        (update-in [:x] #(+ x %))
        (update-in [:y] #(+ y %)))))

(defn rectangle-center [rectangle]
  (let [{:keys [x y width height]} rectangle]
    [(+ x (/ width 2))
     (+ y (/ height 2))]))


(defn rectangle-origin [rectangle]
  (let [{:keys [x y]} rectangle]
    [x y]))

(defn rectangle-hit-test [rectangle position]
  (let [{:keys [x y width height]} rectangle
        [px py] position]
    (and (>= px x)
         (<= px (+ x width))
         (>= py y)
         (<= py (+ y height)))))

(defn rectangles-overlap? [ra rb]
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

(defn point-add [p1 p2]
  (let [[x1 y1] p1
        [x2 y2] p2]
    [(+ x1 x2)
     (+ y1 y2)]))


(defn point-sub [p1 p2]
  (let [[x1 y1] p1
        [x2 y2] p2]
    [(- x1 x2)
     (- y1 y2)]))
