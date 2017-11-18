(ns dynadoc.common
  (:require [rum.core :as rum]))

(rum/defc app < rum/reactive [state]
  (let [{:keys [nses ns vars]} @state]
    [:div
     (into [:div {:class "nses"}]
       (mapv (fn [sym]
               [:div [:a {:href (str "/" sym)}
                      (str sym)]])
         nses))
     (into [:div {:class "vars"}]
       (mapv (fn [sym]
               [:div [:a {:href (str "/" ns "/" sym)}
                      (str sym)]])
         vars))]))

