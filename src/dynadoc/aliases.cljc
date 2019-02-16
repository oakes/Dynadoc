(ns dynadoc.aliases
  (:require [clojure.core.async :as async]))

(def chan async/promise-chan)

(def put! async/put!)

(def <!! #?(:clj async/<!! :cljs identity))

