(ns alder.math)

(def identity-matrix
  [1 0 0
   0 1 0
   0 0 1])

(defn make-translate [[x y]]
  [1 0 x
   0 1 y
   0 0 1])

(defn mult [a b]
  (let [[a11 a12 a13 a21 a22 a23 a31 a32 a33] a
        [b11 b12 b13 b21 b22 b23 b31 b32 b33] b]
    [(+ (* a11 b11) (* a12 b21) (* a13 b31))
     (+ (* a11 b12) (* a12 b22) (* a13 b32))
     (+ (* a11 b13) (* a12 b23) (* a13 b33))

     (+ (* a21 b11) (* a22 b21) (* a23 b31))
     (+ (* a21 b12) (* a22 b22) (* a23 b32))
     (+ (* a21 b13) (* a22 b23) (* a23 b33))

     (+ (* a31 b11) (* a32 b21) (* a33 b31))
     (+ (* a31 b12) (* a32 b22) (* a33 b32))
     (+ (* a31 b13) (* a32 b23) (* a33 b33))]))

(defn mult-point [m p]
  (let [[m11 m12 m13 m21 m22 m23 m31 m32 m33] m
        [x y] p]
    [(+ (* m11 x) (* m12 y) m13)
     (+ (* m21 x) (* m22 y) m23)]))
