(ns dynadoc.common
  (:require [rum.core :as rum]
            [html-soup.core :as hs]))

(def meta-keys [:file :arglists :doc])

(defn var->html [{:keys [var-sym tests? instarepl? toggle-tests toggle-instarepl]}
                 {:keys [sym url meta source spec examples]}]
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
       (into [:div
              [:h2
               (if (= (count examples) 1)
                 "Example"
                 "Examples")
               [:div {:class "button"
                      :on-click toggle-tests}
                (if tests?
                  "Hide Tests"
                  "Show Tests")]
               [:div {:class "button"
                      :on-click toggle-instarepl}
                (if instarepl?
                  "Hide InstaREPL"
                  "Show InstaREPL")]]]
         (mapv (fn [{:keys [doc def]}]
                 [:div {:class "section"}
                  [:div {:class "section doc"} doc]
                  [:div {:class "paren-soup"}
                   [:div {:class "content"
                          :dangerouslySetInnerHTML {:__html (hs/code->html def)}}]]])
           examples)))
     (when (and var-sym source)
       [:div {:class "section"}
        [:h2 "Source"]
        [:div {:class "paren-soup"}
         [:div {:class "content"
                :dangerouslySetInnerHTML {:__html (hs/code->html source)}}]]])]))

(rum/defc app < rum/reactive [state]
  (let [{:keys [nses vars] :as state} (rum/react state)]    
    [:div
     (into [:div {:class "nses"}]
       (mapv (fn [sym]
               [:div [:a {:href (str "/" sym)}
                      (str sym)]])
         nses))
     (into [:div {:class "vars"}]
       (mapv (partial var->html state) vars))]))

