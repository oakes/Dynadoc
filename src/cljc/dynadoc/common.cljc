(ns dynadoc.common
  (:require [rum.core :as rum]
            [html-soup.core :as hs]))

(def meta-keys [:file :arglists :doc])

(rum/defcs expandable-section < (rum/local false ::expanded?)
  [state label url content]
  (let [expanded? (::expanded? state)]
    [:div {:class "section"}
     [:a {:href url
          :on-click (fn [e]
                      (.preventDefault e)
                      (swap! expanded? not))}
      [:h3 (str (if @expanded? "- " "+ ") label)]]
     (when @expanded?
       @content)]))

(defn examples->html [examples]
  (mapv (fn [{:keys [doc def]}]
          [:div {:class "section"}
           [:div {:class "section doc"} doc]
           [:div {:class "paren-soup"}
            [:div {:class "content"
                   :dangerouslySetInnerHTML {:__html (hs/code->html def)}}]]])
    examples))

(defn source->html [source]
  [:div {:class "paren-soup"}
   [:div {:class "content"
          :dangerouslySetInnerHTML {:__html (hs/code->html source)}}]])

(defn var->html [{:keys [var-sym eval? toggle-eval]}
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
     (when (seq examples)
       (if var-sym
         (into [:div {:class "section"}
                [:h2 "Examples"
                 [:div {:class "button var-button"
                        :on-click toggle-eval}
                  (if eval?
                    "Stop"
                    "Start")]]]
           (examples->html examples))
         (expandable-section "Examples" url
           (delay (into [:div] (examples->html examples))))))
     (when source
       (if var-sym
         [:div {:class "section"}
          [:h2 "Source"]
          (source->html source)]
         (expandable-section "Source" url (delay (source->html source)))))]))

(rum/defc app < rum/reactive [state]
  (let [{:keys [nses ns-sym ns-meta var-sym vars] :as state} (rum/react state)]
    [:div
     (into [:div {:class "nses"}]
       (mapv (fn [sym]
               [:div [:a {:href (str "/" sym)}
                      (str sym)]])
         nses))
     (if ns-sym
       (into [:div {:class "vars"}
              (when-not var-sym
                [:div
                 [:center [:h1 (str ns-sym)]]
                 (when-let [doc (:doc ns-meta)]
                   [:div {:class "section doc"} doc])])]
         (mapv (partial var->html state) vars))
       [:div {:class "vars"}
        [:center [:h1 "Welcome to Dynadoc"]]])]))

