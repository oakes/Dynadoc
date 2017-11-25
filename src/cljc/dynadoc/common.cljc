(ns dynadoc.common
  (:require [rum.core :as rum]
            [html-soup.core :as hs]
            [clojure.string :as str]))

(def meta-keys [:file :arglists :doc])

(defn ns-sym->url [type ns-sym]
  (str "/" (name type) "/" ns-sym))

(defn var-sym->url [type ns-sym var-sym]
  (str "/" (name type) "/" ns-sym "/"
    #?(:cljs (js/escape (str var-sym))
       :clj (java.net.URLEncoder/encode (str var-sym) "UTF-8"))))

(rum/defcs expandable-section < (rum/local false ::expanded?)
  [state label url *content]
  (let [*expanded? (::expanded? state)]
    [:div {:class "section"}
     [:a {:href url
          #?@(:cljs [:on-click (fn [e]
                                 (.preventDefault e)
                                 (swap! *expanded? not))])}
      [:h3 (str (if @*expanded? "- " "+ ") label)]]
     (when @*expanded?
       @*content)]))

(defn example->html [hide-instarepl? {:keys [id doc body-str with-card]}]
  [:div {:class "section"}
   [:div {:class "section doc"} doc]
   (when with-card
     [:div {:class "card" :id id}])
   [:div {:class "paren-soup example"}
    (when-not hide-instarepl?
      [:div {:class "instarepl" :style {:display "list-item"}}])
    [:div {:class "content"
           :dangerouslySetInnerHTML {:__html (hs/code->html body-str)}}]]])

(defn source->html [source]
  [:div {:class "paren-soup nonedit"}
   [:div {:class "content"
          :dangerouslySetInnerHTML {:__html (hs/code->html source)}}]])

(defn var->html [{:keys [ns-sym var-sym type disable-cljs-instarepl?] :as state}
                 {:keys [sym meta source spec examples]}]
  (let [{:keys [arglists doc]} meta
        url (var-sym->url type ns-sym sym)]
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
        [:div {:class "paren-soup nonedit"}
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

(rum/defcs sidebar  < (rum/local "" ::search)
  [state {:keys [nses cljs-started?]}]
  (let [*search (::search state)
        search @*search]
    [:div
     (when cljs-started?
       [:input {:class "search"
                :on-change #(->> % .-target .-value (reset! *search))
                :placeholder "Search"}])
     (into [:div {:class "nses"}]
       (keep (fn [{:keys [sym type var-syms]}]
               (let [vars (when (seq search)
                            (->> var-syms
                                 (filter #(str/index-of (str %) search))
                                 (mapv (fn [var-sym]
                                         [:div {:class "var"}
                                          [:a {:href (var-sym->url type sym var-sym)}
                                           (str var-sym)]]))))]
                 (when (or (empty? search)
                           (str/index-of (str sym) search)
                           (seq vars))
                   [:div
                    (when (= type :cljs)
                      [:div {:class "tag"} "CLJS"])
                    [:a {:href (ns-sym->url type sym)}
                     (str sym)]
                    (when (seq vars)
                      (into [:div] vars))])))
         nses))]))

(rum/defc app < rum/reactive [*state]
  (let [{:keys [ns-sym ns-meta var-sym vars] :as state} (rum/react *state)]
    [:div
     (sidebar state)
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

