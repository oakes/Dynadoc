(ns dynadoc.common
  (:require [rum.core :as rum]))

(def meta-keys [:added :file :arglists :doc])

(rum/defc app < rum/reactive [state]
  (let [{:keys [nses ns-sym var-sym vars]} @state]
    [:div
     (into [:div {:class "nses"}]
       (mapv (fn [sym]
               [:div [:a {:href (str "/" sym)}
                      (str sym)]])
         nses))
     (into [:div {:class "vars"}]
       (mapv (fn [{:keys [sym url meta source]}]
               (let [{:keys [arglists doc]} meta]
                 [:div
                  (into (if var-sym
                          [:div]
                          [:a {:href (str "/" ns-sym "/" url)}])
                    (if arglists
                      (map (fn [arglist]
                             [:h2 (pr-str (apply list sym arglist))])
                        arglists)
                      [[:h2 (str sym)]]))
                  (when doc
                    [:div {:class "doc"} doc])]))
         vars))]))

