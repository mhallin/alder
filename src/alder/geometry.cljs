(ns alder.geometry)

(defrecord Rectangle
    [x y width height])

(defn rectangle->css [rectangle]
  (let [{:keys [x y width height]} rectangle]
    {:top (str y "px")
     :left (str x "px")
     :width (str width "px")
     :height (str height "px")}))

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
