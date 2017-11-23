(ns dynadoc.common
  (:require [rum.core :as rum]
            [html-soup.core :as hs]))

(def meta-keys [:file :arglists :doc])

(rum/defcs expandable-section < (rum/local false ::expanded?)
  [state label url content]
  (let [expanded? (::expanded? state)]
    [:div {:class "section"}
     [:a {:href url
          #?@(:cljs [:on-click (fn [e]
                                 (.preventDefault e)
                                 (swap! expanded? not))])}
      [:h3 (str (if @expanded? "- " "+ ") label)]]
     (when @expanded?
       @content)]))

(defn example->html [hide-instarepl? {:keys [id doc body-str with-card]}]
  [:div {:class "section"}
   [:div {:class "section doc"} doc]
   (when with-card
     [:div {:class "card" :id id}])
   [:div {:class "paren-soup"}
    (when-not hide-instarepl?
      [:div {:class "instarepl" :style {:display "list-item"}}])
    [:div {:class "content edit"
           :dangerouslySetInnerHTML {:__html (hs/code->html body-str)}}]]])

(defn source->html [source]
  [:div {:class "paren-soup"}
   [:div {:class "content"
          :dangerouslySetInnerHTML {:__html (hs/code->html source)}}]])

(defn var->html [{:keys [var-sym type disable-cljs-instarepl?] :as state}
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
                [:h2 "Example"]]
           (mapv (partial example->html (and (= type :cljs) disable-cljs-instarepl?))
             examples))
         (expandable-section "Example" url
           (delay (into [:div] (mapv (partial example->html true) examples))))))
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
       (mapv (fn [{:keys [sym type url]}]
               [:div
                (when (= type :cljs)
                  [:div {:class "tag"} "CLJS"])
                [:a {:href url}
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

