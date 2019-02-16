(ns dynadoc.prod
  (:require [dynadoc.core :as c]))

(swap! c/*state assoc :prod? true)
(c/init)
