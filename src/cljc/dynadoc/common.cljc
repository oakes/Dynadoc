(ns dynadoc.common
  (:require [rum.core :as rum]
            [html-soup.core :as hs]
            [clojure.string :as str]))

(def ^:const page-url "https://clojars.org/dynadoc")

(defn ns-sym->url [rel-path static? type ns-sym]
  (str rel-path (name type) "/" ns-sym (when static? ".html")))

(defn var-sym->url [rel-path static? type ns-sym var-sym]
  (str rel-path (name type) "/" ns-sym "/"
    (if static?
      (str/replace (str var-sym) "?" "_q")
      #?(:cljs (js/escape (str var-sym))
         :clj (java.net.URLEncoder/encode (str var-sym) "UTF-8")))
    (when static? ".html")))

(rum/defcs expandable-section < (rum/local false ::expanded?)
  [rum-state {:keys [label url *content on-close]}]
  (let [*expanded? (::expanded? rum-state)]
    [:div {:class "section"}
     [:a {:href url
          #?@(:cljs [:on-click (fn [e]
                                 (.preventDefault e)
                                 (when-not (swap! *expanded? not)
                                   (when on-close
                                     (on-close))))])}
      [:h3 (str (if @*expanded? "- " "+ ") label)]]
     (when @*expanded?
       @*content)]))

(rum/defc example->html [hide-instarepl? {:keys [id doc body-str with-card]}]
  (when-let [html (try
                    (hs/code->html body-str)
                    (catch #?(:clj Exception :cljs js/Error) _))]
    [:div {:class "section"}
     [:div {:class "section doc"} doc]
     (when (and with-card (not hide-instarepl?))
       [:div {:class "card" :id id}])
     [:div {:class "paren-soup example"}
      [:div {:class "instarepl"
             :style {:display (if hide-instarepl? "none" "list-item")}}]
      [:div {:class "content"
             :dangerouslySetInnerHTML {:__html html}}]]]))

(rum/defc source->html [source]
  (when-let [html (try
                    (hs/code->html source)
                    (catch #?(:clj Exception :cljs js/Error) _))]
    [:div {:class "paren-soup nonedit"}
     [:div {:class "content"
            :dangerouslySetInnerHTML {:__html html}}]]))

(rum/defc spec->html [spec]
  (when-let [html (try
                    (hs/code->html spec)
                    (catch #?(:clj Exception :cljs js/Error) _))]
    [:div {:class "paren-soup nonedit"}
     [:div {:class "content"
            :dangerouslySetInnerHTML {:__html html}}]]))

(rum/defc var->html
  [{:keys [ns-sym var-sym type prod? rel-path static?] :as state}
   {:keys [sym meta source spec examples]}]
  (let [{:keys [arglists doc]} meta
        url (var-sym->url rel-path static? type ns-sym sym)]
    [:div
     (into (if var-sym
             [:div]
             [:a {:href url}])
       (if arglists
         (map (fn [arglist]
                [:h2 (pr-str (apply list sym arglist))])
           arglists)
         [[:h2 (str sym)]]))
     (when doc
       [:div {:class "section doc"} doc])
     (when (seq examples)
       (into [:div {:class "section"}
                [:h2 "Example"]]
           (mapv (partial example->html
                   (or (and (= type :cljs) prod?)
                       (and (= type :clj) static?)))
             examples)))
     (when spec
       (if var-sym
         [:div {:class "section"}
          [:h2 "Spec"]
          (spec->html spec)]
         (expandable-section
           {:label "Spec"
            :url url
            :*content (delay (spec->html spec))})))
     (when source
       (if var-sym
         [:div {:class "section"}
          [:h2 "Source"]
          (source->html source)]
         (expandable-section
           {:label "Source"
            :url url
            :*content (delay (source->html source))})))]))

(rum/defcs sidebar  < (rum/local "" ::search)
  [rum-state {:keys [nses cljs-started? export-filter rel-path static?]}]
  (let [*search (::search rum-state)
        search (or export-filter @*search)
        search (when (seq search)
                 (re-pattern search))]
    [:div
     (when cljs-started?
       [:input {:class "search"
                :on-change #(->> % .-target .-value (reset! *search))
                :placeholder "Search"}])
     (into [:div {:class "nses"}
            (when (seq export-filter)
              [:i "Pages to export:"])]
       (keep (fn [{:keys [sym type var-syms]}]
               (let [vars (when (and search (empty? export-filter))
                            (->> var-syms
                                 (filter #(re-find search (str %)))
                                 (mapv (fn [var-sym]
                                         [:div {:class "var"}
                                          [:a {:href (var-sym->url rel-path static? type sym var-sym)}
                                           (str var-sym)]]))))]
                 (when (or (nil? search)
                           (re-find search (str sym))
                           (seq vars))
                   [:div
                    (when (= type :cljs)
                      [:div {:class "tag"} "CLJS"])
                    [:a {:href (ns-sym->url rel-path static? type sym)}
                     (str sym)]
                    (when (seq vars)
                      (into [:div] vars))])))
         nses))]))

(rum/defcs export-form < (rum/local {} ::options)
  [rum-state {:keys [type ns-sym var-sym export-filter]} *state]
  (let [*options (::options rum-state)
        {:keys [pages]
         :or {pages (if ns-sym :single :multiple)}} @*options]
    [:form {:action "/dynadoc-export.zip"
            :method :get
            :style {:text-align "left"}}
     [:div
      [:label
       [:input {:type "radio" :name "pages" :value "single" :checked (= pages :single)
                :on-click #(swap! *options assoc :pages :single)
                :disabled (nil? ns-sym)}]
       "Only this page"]]
     [:div
      [:label
       [:input {:type "radio" :name "pages" :value "multiple" :checked (= pages :multiple)
                :on-click #(swap! *options assoc :pages :multiple)}]
       "Multiple pages"]]
     [:div {:style {:margin 10 :font-size 14}}
      (case pages
        :single [:i
                 "Only the current page will be exported"
                 [:br]
                 "and the sidebar will be hidden."]
        :multiple [:i
                   "All the namespaces in the sidebar"
                   [:br]
                   "will be exported. You can narrow them"
                   [:br]
                   "down with the following regex:"
                   [:div
                    [:input {:type "text"
                             :placeholder "Export filter"
                             :style {:margin 5 :font-size 14}
                             :on-change #(->> % .-target .-value
                                              (swap! *state assoc :export-filter))}]]])]
     [:input {:type "hidden" :name "export-filter" :value export-filter}]
     (when type
       [:input {:type "hidden" :name "type" :value (name type)}])
     (when ns-sym
       [:input {:type "hidden" :name "ns-sym" :value (str ns-sym)}])
     (when var-sym
       [:input {:type "hidden" :name "var-sym" :value (str var-sym)}])
     [:div {:style {:text-align "center"}}
      [:button {:type "submit"}
       "Download zip file"]]]))

(rum/defc export [{:keys [cljs-started? static?] :as state} *state]
  (when-not static?
    [:div {:style {:min-height 75}}
     (when cljs-started?
       [:div {:class "export"}
        (expandable-section
          {:label "Export"
           :url ""
           :*content (delay (export-form state *state))
           :on-close #(swap! *state dissoc :export-filter)})])
     [:div {:style {:clear "right"}}]]))

(rum/defc update-link []
  [:div
   [:a {:href page-url
        :target "_blank"}
    "New version of Dynadoc!"]])

(rum/defc app < rum/reactive [*state]
  (let [{:keys [ns-sym ns-meta var-sym vars
                cljs-started? prod? static? hide-sidebar? update?]
         :as state} (rum/react *state)]
    [:div
     (when-not hide-sidebar?
       (sidebar state))
     (conj
       (if ns-sym
         (into [:div {:class "vars"
                      :style {:left (if hide-sidebar? 0 300)}}
                (export state *state)
                (when-not var-sym
                  [:div
                   [:center [:h1 (str ns-sym)]]
                   (when-let [doc (:doc ns-meta)]
                     [:div {:class "section doc"} doc])])]
           (mapv (partial var->html state) vars))
         [:div {:class "vars"}
          (export state *state)])
       (cond
         static?
         [:div {:class "footer"}
          "Generated by "
          [:a {:href "https://github.com/oakes/Dynadoc"
               :target "_blank"}
           "Dynadoc"]
          (when update?
            (update-link))]
         (and cljs-started? (not prod?))
         [:div {:class "footer"}
          "This is a custom build of "
          [:a {:href "https://github.com/oakes/Dynadoc"
               :target "_blank"}
           "Dynadoc"]
          (when update?
            (update-link))]))]))

