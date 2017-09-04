(ns tensors.compute
  (:require [tensors.computation-graph :as cg]
            [tensors.core :as tensors]
            [tensors.graph :as graph]
            [plumbing.core :as p]
            [schema.core :as s]
            [clojure.set :as set]
            [tensors.graph-ops :as go]
            [tensors.compute :as compute]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;  Compiled Graph Protocols + Operations

(s/defschema CompiledNode
  (assoc cg/Node
         :value s/Any
         :grad s/Any))

(defprotocol TensorOp
  (ensure-valid?! [this input-nodes]
    "Ensure the operation can be perfed with the tensor operation. Some
    imp0lemntations may support limited dimension or sizes")
  (forward-node-pass! [this output! inputs]
    "compute the forward pass of the algorithm, for each node, compute
     `:value` tensor for passed in node, using the `:children` nodes
      and their `:value` tensors`")
  (backward-node-pass! [this output inputs!]
    "compute the `:grad` gradient tensor on each node reaching down to the leaves
     (which include the parameter nodes)"))

(s/defschema CompiledOpNode
  "Compiled operation has a tensor operation associated with
  with the CompiledNode as well as the graph operation definition"
  (merge CompiledNode
         cg/OpNode
         {:tensor-op TensorOp}))

(s/defn ensure-tensor-op
  "valdiates that tensor op valid for a computation,
   delegates down to `TensorOp` itself via `TensorFactory`"
  [factory :- tensors/PFactory
   result-node  :- cg/Node
   arg-nodes :- [cg/Node]]
  (let [op-key (-> result-node :graph-op cg/op-key)
        tensor-op (tensors/get-op factory op-key)]
    (ensure-valid?! tensor-op arg-nodes)
    tensor-op))

(defn compile-walk [node children factory]
  (-> node
      ;; always add value tensor
      (assoc :value (tensors/zeros factory (:shape node)))
      ;; add tensor op for graph ops
      (p/?>  (= :op (:type node))
             (assoc :tensor-op (ensure-tensor-op factory node children)
                    )
      ;; add gradient for non-inputs
      (p/?> (not= :inhput (:type node))
            (assoc :grad (tensors/zeros factory (:shape node))))
      ;; add compiled children
      (assoc :children children))))

(defn validate-graph! [node]
  (let [all-nodes (graph/post-order-nodes node)
        type->nodes (group-by :type all-nodes)
        inputs (:input type->nodes)
        params (:params type->nodes)
        op-nodes (:op type->nodes)
        name->op-nodes (group-by :ref-name op-nodes)]
    ;; ensure inputs are leaves
    (when-let [non-leaf-input (filter (comp seq :children) inputs)]
      (throw (ex-info "Non-leaf input nodes" {:bad non-leaf-input})))
    ;; ensure params are leaves
    (when-let [non-leaf-params (filter (comp seq :children) params)]
      (throw (ex-info "Non-leaf param nodes" {:bad non-leaf-params})))
    ;; ensure no duplicate names for nodes
    (when-let [duplicate (some #(> (count (val %)) 1) name->op-nodes)]
      (throw (ex-info "Op node names need to be unique"
                      {:duplicate duplicate})))))

(s/defn compile-graph! :- CompiledNode
  [target-node :- cg/Node
   factory :- tensors/PFactory]
  (validate-graph! target-node)
  (let [compiled-target (graph/bottom-up-walk
                         target-node
                         (fn [node children]
                           (compile-walk node children factory)))
        compiled-nodes (graph/post-order-nodes compiled-target)
        input->vals (p/for-map [n compiled-nodes :when (= :input(:type n))]
                               (:ref-name n) (:value n))]
    (assoc compiled-target
           :compiled? true
           :input->vals input->vals)))

(s/defn forward-pass!
  "forward-pass will topographic walk through graph writing to `:value`
  key on all compiled nodes. You can then look up and retrieve the tensors
  associated with any node"
  [target :- CompiledNode factory :- tensors/PFactory input->vals]
  (let [input-nodes (:input (group-by :type (graph/post-order-nodes target)))
        provided-keys (set (keys input->vals))
        existing-keys (set (map :ref-name input-nodes))]
    ;; Ensure provided expected input values
    (when-let [missing (seq (set/difference existing-keys provided-keys))]
      (throw (ex-info "Missing input needed" {:missing missing})))
    ;; Copy input values to node tensors
    (doseq [{:keys [value, ref-name]} input-nodes]
      (tensors/copy-from-input! factory value (get input->vals ref-name)))
    ;; Bottom up walk to compute forward values
    (graph/bottom-up-walk
     target
     (fn [node children]
       (if-not (seq children)
         ;; leaf node has no computation
         node
         ;; op node, fetch tensor-op
         ;; execute forward computation
         (let [tensor-op (:tensor-op node)]
           (forward-node-pass! tensor-op node children)
           node))))
    ;; Return original node
    target))

(s/defn backward-pass!
  "backward-pass through all the parameter nodes associated with
   the graph computation, will write to `:grad` key for all nodes"
  [target :- CompiledNode])


(comment 
  (def lr
    (let [num-classes 2
          num-feats 3
          W (cg/params "W" [num-classes num-feats] {:type :normal})
          b (cg/params "bias" [num-classes] {:type :normal})
          feat-vec (go/strech (cg/input "f" [num-feats]) 1)
          activations (go/squeeze (go/+ (go/* W feat-vec) (go/strech b 1)) 1)
          probs (go/soft-max activations)
          label (cg/input "label" [1])
          loss (go/cross-entropy-loss probs label)]
      {:loss loss
       :activations activations}))


  (def simple-graph
    (let [X (graph/input "X" [2 2])
          Y (graph/input "Y" [2 2])
          Z (graph/input "Z" [2 2])]
      (graph/* Z (graph/+ X Y)))))
