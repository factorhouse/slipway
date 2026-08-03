(ns slipway.principal
  (:refer-clojure :exclude [name type]))

(defn from
  [type name]
  {::type type
   ::name name})

(def name ::name)

(def type ::type)