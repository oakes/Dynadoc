(ns dynadoc.common
  (:require [rum.core :as rum]
            [html-soup.core :as hs]
            [clojure.string :as str]
            [odoyle.rules :as o]
            [odoyle.rum :as orum]))

(defn ns-sym->url [rel-path static? type ns-sym]
  (str rel-path (name type) "/" ns-sym (when static? ".html")))

(defn var-sym->url [rel-path static? type ns-sym var-sym]
  (str rel-path (name type) "/" ns-sym "/"
    (if static?
      (-> (str var-sym)
          (str/replace "?" "'q'")
          (str/replace "!" "'e'")
          (str/replace "<" "'l'")
          (str/replace ">" "'g'")
          (str/replace ":" "'c'")
          (str/replace "*" "'a'")
          (str/replace "&" "'m'"))
      #?(:cljs (js/encodeURIComponent (str var-sym))
         :clj (java.net.URLEncoder/encode (str var-sym) "UTF-8")))
    (when static? ".html")))

(rum/defcs expandable-section < (rum/local false ::*expanded?)
  [{:keys [::*expanded?] :as rum-state} {:keys [label url *content on-close]}]
  [:div {:class "section"}
   [:a {:href url
        #?@(:cljs [:on-click (fn [e]
                               (.preventDefault e)
                               (when-not (swap! *expanded? not)
                                 (when on-close
                                   (on-close))))])}
    [:h3 (str (if @*expanded? "- " "+ ") label)]]
   (when @*expanded?
     @*content)])

(defn init-editor [rum-state]
  (let [[state] (:rum/args rum-state)]
    (when-let [init (:init-editor state)]
      (init (rum/dom-node rum-state))))
  rum-state)

(defn init-example-editor [rum-state]
  (let [[state example] (:rum/args rum-state)]
    (when-let [init (:init-example-editor state)]
      (init (rum/dom-node rum-state) example)))
  rum-state)

(rum/defc example->html < {:after-render init-example-editor}
  [{:keys [type prod? static?] :as state}
   {:keys [id doc body-str with-card] :as example}]
  (when-let [html (try
                    (hs/code->html (str body-str \newline))
                    (catch #?(:clj Exception :cljs js/Error) e
                      (println e)))]
    (let [hide-instarepl? (or (and (= type :cljs) prod?)
                              (and (= type :clj) static?))]
      [:div {:class "section"}
       [:div {:class "section doc"} doc]
       (when (and with-card (not hide-instarepl?))
         [:div {:class "card" :id id}])
       [:div {:class "paren-soup"}
        [:div {:class "instarepl"
               :style {:display (if hide-instarepl? "none" "list-item")}}]
        [:div {:class "content"
               :dangerouslySetInnerHTML {:__html html}}]]])))

(rum/defc source->html < {:after-render init-editor}
  [state source]
  (when-let [html (try
                    (hs/code->html (str source \newline))
                    (catch #?(:clj Exception :cljs js/Error) e
                      (println e)))]
    [:div {:class "paren-soup"}
     [:div {:class "content"
            :dangerouslySetInnerHTML {:__html html}}]]))

(rum/defc spec->html < {:after-render init-editor}
  [state spec]
  (when-let [html (try
                    (hs/code->html (str spec \newline))
                    (catch #?(:clj Exception :cljs js/Error) e
                      (println e)))]
    [:div {:class "paren-soup"}
     [:div {:class "content"
            :dangerouslySetInnerHTML {:__html html}}]]))

(rum/defc var->html
  [{:keys [ns-sym var-sym type rel-path static?] :as state}
   {:keys [sym meta source spec examples methods protocol]}]
  (let [{:keys [arglists doc]} meta
        url (var-sym->url rel-path static? type ns-sym sym)]
    [:div {:class "var-info"}
     (into (if var-sym
             [:div]
             [:a {:href url}])
       (if arglists
         (map (fn [arglist]
                [:h2 (pr-str (apply list sym arglist))])
           arglists)
         [[:h2 (str sym)]]))
     (when methods
       [:div {:class "section"}
        [:h3 "Methods in this protocol"]
        (into [:ul]
          (for [method-sym methods]
            [:li [:a {:href (var-sym->url rel-path static? type ns-sym method-sym)}
                  (str method-sym)]]))])
     (when protocol
       [:div {:class "section"}
        [:h3
         "Part of the "
         [:a {:href (var-sym->url rel-path static? type ns-sym protocol)}
          (str protocol)]
         " protocol"]])
     (when doc
       [:div {:class "section doc"} doc])
     (when (seq examples)
       (into [:div {:class "section"}
              [:h2 (if (> (count examples) 1) "Examples" "Example")]]
         (mapv (partial example->html state)
           examples)))
     (when spec
       (if var-sym
         [:div {:class "section"}
          [:h2 "Spec"]
          (spec->html state spec)]
         (expandable-section
           {:label "Spec"
            :url url
            :*content (delay (spec->html state spec))})))
     (when source
       (if var-sym
         [:div {:class "section"}
          [:h2 "Source"]
          (source->html state source)]
         (expandable-section
           {:label "Source"
            :url url
            :*content (delay (source->html state source))})))]))

(def rules
  (o/ruleset
    {::get-server-state
     [:what
      [::server ::ns-sym ns-sym]
      [::server ::var-sym var-sym]
      [::server ::type type]]}))

(def components
  (orum/ruleset
    {::app-root
     [:what
      [::server ::ns-sym ns-sym]
      [::server ::ns-meta ns-meta]
      [::server ::var-sym var-sym]
      [::server ::vars vars]
      [::client ::cljs-started? cljs-started?]
      [::client ::prod? prod?]
      [::server ::static? static?]
      [::server ::hide-sidebar? hide-sidebar?]
      :then
      (let [*state (orum/prop)
            state @*state]
        [:div
         (when-not hide-sidebar?
           (sidebar state))
         (conj
           (if ns-sym
             (into [:div {:class "vars"
                          :style {:left (if hide-sidebar? 0 300)}}
                    (export *state)
                    (when-not var-sym
                      [:div
                       [:center [:h1 (str ns-sym)]]
                       (when-let [doc (:doc ns-meta)]
                         [:div {:class "section doc"} doc])])]
               (mapv (partial var->html state) vars))
             [:div {:class "vars"}
              (export *state)])
           (cond
             static?
             [:div {:class "footer"}
              "Generated by "
              [:a {:href "https://github.com/oakes/Dynadoc"
                   :target "_blank"}
               "Dynadoc"]]
             (and cljs-started? (not prod?))
             [:div {:class "footer"}
              "This is a custom build of "
              [:a {:href "https://github.com/oakes/Dynadoc"
                   :target "_blank"}
               "Dynadoc"]]))])]
     
     ::export
     [:what
      [::client ::cljs-started? cljs-started?]
      [::server ::static? static?]
      :then
      (let [*state (orum/prop)]
        (when-not static?
          [:div {:style {:min-height 75}}
           (when cljs-started?
             [:div {:class "export"}
              (expandable-section
                {:label "Export"
                 :url ""
                 :*content (delay (export-form *state))
                 :on-close #(swap! *state dissoc :export-filter)})])
           [:div {:style {:clear "right"}}]]))]
     
     ::export-form
     [:what
      [::server ::ns-sym ns-sym]
      [::server ::ns-meta ns-meta]
      [::server ::var-sym var-sym]
      [::server ::type type]
      [::client ::exportable? exportable?]
      :then
      (let [*state (orum/prop)
            export-filter (:export-filter @*state)
            *options (orum/atom {:pages (if ns-sym :single :multiple)})
            {:keys [pages]} @*options]
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
                                 :value ""
                                 :placeholder "Export filter"
                                 :style {:margin 5 :font-size 14}
                                 :on-change #(->> % .-target .-value
                                                  (swap! *state assoc :export-filter))}]]])]
         [:input {:type "hidden" :name "export-filter" :value (or export-filter "")}]
         (when type
           [:input {:type "hidden" :name "type" :value (name type)}])
         (when ns-sym
           [:input {:type "hidden" :name "ns-sym" :value (str ns-sym)}])
         (when var-sym
           [:input {:type "hidden" :name "var-sym" :value (str var-sym)}])
         [:div {:style {:text-align "center"}}
          [:button {:type "submit"
                    :disabled (not exportable?)}
           "Download zip file"]]
         (when-not exportable?
           [:div {:style {:margin 10 :font-size 14}}
            [:div [:b "You built Dynadoc with :optimizations set to :none"]]
            [:div [:b "You must set it to :simple in order to export"]]])])]

     ::sidebar
     [:what
      [::server ::nses nses]
      [::client ::cljs-started? cljs-started?]
      [::server ::rel-path rel-path]
      [::server ::static? static?]
      [::server ::hide-badge? hide-badge?]
      :then
      (let [state (orum/prop)
            export-filter (:export-filter state)
            *search (orum/atom "")
            search (or export-filter @*search)
            search (when (seq search)
                     (try (re-pattern search)
                       (catch #?(:clj Exception :cljs js/Error) e
                         (println e))))]
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
                        (when (and (= type :cljs) (not hide-badge?))
                          [:div {:class "tag"} "CLJS"])
                        [:a {:href (ns-sym->url rel-path static? type sym)}
                         (str sym)]
                        (when (seq vars)
                          (into [:div] vars))])))
             nses))])]}))

(def *session
  (-> (reduce o/add-rule (o/->session) (concat rules components))
      atom))

(defn update-session [id state]
  (swap! *session
         (fn [session]
           (o/fire-rules
             (reduce-kv
               (fn [session k v]
                 (o/insert session id k v))
               session
               state))))
  state)

(defn get-server-state []
  (-> @*session
       (o/query-all ::get-server-state)
       first
       (or (throw (ex-info "Server state not found" {})))))

(rum/defc app [*state]
  (app-root *state))

