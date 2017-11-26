(ns dynadoc.examples
  (:require [dynadoc.state :refer [*state]])
  (:require-macros [dynadoc.example :refer [defexamples]]))

(swap! *state assoc :dev? true)

(defexamples dynadoc.core/form->serializable
  [{:doc "This is a test example"
    :with-focus [focus (+ a b)]}
   (let [a 1
         b 2]
     focus)]
  ["Serialize an error"
   (form->serializable (js/Error. "This is an error!"))])

