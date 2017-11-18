(ns dynadoc.common
  (:require [rum.core :as rum]))

(rum/defc app < rum/reactive [state]
  (let [{:keys [ns-names]} @state]
    (into [:div]
      (mapv (fn [n]
              [:div (str n)])
        ns-names))))

