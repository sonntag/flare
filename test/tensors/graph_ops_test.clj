(ns tensors.graph-ops-test
  (:refer-clojure :exclude [+ * concat])
  (:require [tensors.graph-ops :refer :all]
            [tensors.computation-graph :as cg]
            [tensors.core :as tensors]
            [clojure.test :refer :all]))

(deftest arithcmetic-test
  (testing "simple addition"
    (let [Y (cg/input "Y" [1 10])
          X (cg/input "X" [1 10])
          L (cg/input "L" [1 5])
          Z (+ X Y)]
      (are [k v] (= (get Z k) v)
        :shape [1 10]
        :graph-op (->SumGraphOp)
        :children [X Y])
      (is (thrown? RuntimeException (+ X L)))))
  (testing "simple multiplication"
    (let [Y (cg/input "Y" [1 10])
          X (cg/input "X" [10 1])
          Z (* X Y)
          Z-rev (* Y X)]
      (is (thrown? RuntimeException (* Z Y)))
      (are [k v] (= (get Z k) v)
        :shape [10 10]
        :graph-op (->MultGraphOp)
        :children [X Y])
      (are [k v] (= (get Z-rev k) v)
        :shape [1 1]
        :graph-op (->MultGraphOp)
        :children [Y X]))))

(deftest hadamard-test
  (testing "hadamard"
    (let [X (cg/input "X" [5 5])
          Y (cg/input "Y" [5 5])]
      (is (= [5 5] (:shape (hadamard X Y))))
      (is (thrown? RuntimeException (hadamard X (cg/input [5 1])))))))


(deftest logistic-regression-test
  (testing "create logistic regression graph (make parameters inputs)"
    (let [num-classes 2
          num-feats 10
          W (cg/input "W" [num-classes num-feats])
          b (cg/input "bias" [num-classes])
          feat-vec (cg/input "f" [num-feats])
          activations (+ (* W feat-vec) b)
          label (cg/input "label" [1])
          loss (cross-entropy-loss activations label)]
      (tensors/scalar-shape? (:shape loss)))))

(deftest concat-op-test
  (testing "concat op"
    (let [op (->ConcatOp 0)
          inputs [(cg/input [2 4]) (cg/input [3 4])]]
      (is (= [5 4] (cg/forward-shape op inputs)))
      (cg/op-validate! op inputs)
      (is (thrown? RuntimeException
                   (cg/op-validate! op [(cg/input [3 4]) (cg/input [3 3])]))))))

