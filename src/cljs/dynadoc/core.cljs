(ns dynadoc.core
  (:require [cljs.reader :refer [read-string]]
            [rum.core :as rum]
            [dynadoc.common :as common]))

(defonce state (atom {}))

(reset! state
  (-> (.querySelector js/document "#initial-state")
      .-textContent
      read-string))

(rum/mount (common/app state)
  (.querySelector js/document "#app"))

