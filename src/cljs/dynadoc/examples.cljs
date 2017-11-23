(ns dynadoc.examples
  (:require-macros [dynadoc.example :refer [defexample]]))

(defexamples dynadoc.core/form->serializable
  [{:doc "Serialize code"
    :with-focus [focus (+ a b)]}
   (let [a 1
         b 2]
     focus)]
  ["Serialize an error"
   (form->serializable (js/Error. "This is an error!"))])

