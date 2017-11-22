(ns dynadoc.examples
  (:require-macros [dynadoc.example :refer [defexample]]))

(defexamples dynadoc.core/form->serializable
  ["Serialize code"
   (form->serializable '(+ 1 2 3))]
  ["Serialize an error"
   (form->serializable (js/Error. "This is an error!"))])

