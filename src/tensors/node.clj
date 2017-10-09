(ns tensors.node
  (:require [clojure.string :as str]
            [schema.core :as s]
            [tensors.core :as tensors]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Graph Datastructure

(defrecord Node
    [type shape ref-name value grad graph-op tensor-op children])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Graph Datastructure

(def ^:dynamic *current-input-scope* [])

(defn ^String scoped-name
  "fully qualified node name with scopes"
  [^String node-name]
  (str/join "/" (conj *current-input-scope* node-name)))

(defmacro with-scope [^String scope-name & body]
  `(binding [*current-input-scope*
             (conj *current-input-scope* (name ~scope-name))]
     ~@body))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Making Nodes

(s/defn input :- Node
  "Create input node. The intent is for the node to be re-used
   with different provided tensor values"
  ([input-name :- String shape :- tensors/Shape]
   (map->Node
    {:type :input
     :shape shape
     :ref-name (scoped-name input-name)}))
  ([shape :- tensors/Shape]
   (input (name (gensym "input")) shape)))

(s/defn constant :- Node
  "Create constant variable with provided tensor. Unfortunately also
   need to provide shape since tensor data isn't aware of intended shape "
  [input-name :- String shape :- tensors/PFactory tensor :- s/Any]
  (map->Node
   {:type :constant
    :shape shape
    :value tensor
    :ref-name (scoped-name input-name)}))

(defmacro definput [input-var shape]
  `(def ~input-var (input ~(name input-var) ~shape)))

(defmacro defparams [params-var shape]
  `(def ~params-var (params ~(name params-var) ~shape)))