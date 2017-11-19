(ns dynadoc.common
  (:require [rum.core :as rum]))

(def meta-keys [:file :arglists :doc])

(rum/defc app < rum/reactive [state]
  (let [{:keys [nses ns-sym var-sym vars]} @state]
    [:div
     (into [:div {:class "nses"}]
       (mapv (fn [sym]
               [:div [:a {:href (str "/" sym)}
                      (str sym)]])
         nses))
     (into [:div {:class "vars"}]
       (mapv (fn [{:keys [sym url meta source spec examples]}]
               (let [{:keys [arglists doc]} meta]
                 [:div
                  (into (if var-sym
                          [:div]
                          [:a {:href url}])
                    (if arglists
                      (map (fn [arglist]
                             [:h2 (pr-str (apply list sym arglist))])
                        arglists)
                      [[:h2 (str sym)]]))
                  (when spec
                    [:div {:class "section"}
                     [:h2 "Spec"]
                     [:div {:class "paren-soup"}
                      [:div {:class "content"}
                       (str spec)]]])
                  (when doc
                    [:div {:class "section doc"} doc])
                  (when (and var-sym (seq examples))
                    (into [:div [:h2 (if (= (count examples) 1)
                                       "Example"
                                       "Examples")]]
                      (mapv (fn [{:keys [doc def]}]
                              [:div {:class "section"}
                               [:div {:class "section doc"} doc]
                               [:div {:class "paren-soup"}
                                [:div {:class "content"}
                                 def]]])
                        examples)))
                  (when (and var-sym source)
                    [:div {:class "section"}
                     [:h2 "Source"]
                     [:div {:class "paren-soup"}
                      [:div {:class "content"}
                       source]]])]))
         vars))]))

