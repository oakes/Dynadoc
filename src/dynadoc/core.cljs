(ns dynadoc.core
  (:require [cljs.tools.reader :as r]
            [rum.core :as rum]
            [dynadoc.common :as common]
            [paren-soup.core :as ps]
            [eval-soup.core :as es]
            [goog.object :as gobj]
            [clojure.walk :refer [postwalk]]
            [dynadoc.aliases])
  (:import goog.net.XhrIo))

(defn read-string [s]
  (binding [r/*suppress-read* true]
    (r/read-string {:read-cond :preserve :eof nil} s)))

(defonce *state (atom {}))

(defn with-focus->binding [with-focus]
  (let [form (or (:form with-focus) ;; clojure 1.10
                 (:binding with-focus)) ;; clojure 1.9
        [binding-type binding-val] form]
    (when (#{:local-symbol ;; clojure 1.10
             :sym} ;; clojure 1.9
            binding-type)
      binding-val)))

(defn add-focus [form with-focus body]
  (if-let [binding (with-focus->binding with-focus)]
    (postwalk
      (fn [x]
        (if (= x binding)
          form
          x))
      body)
    form))

(defn add-card [form with-card id]
  (list 'let [with-card (list '.getElementById 'js/document id)] form))

(defn add-callback [form with-callback]
  (list 'let ['es-channel '(dynadoc.aliases/chan)
              with-callback '(fn [data]
                               (dynadoc.aliases/put! es-channel data))]
    form
    '(dynadoc.aliases/<!! es-channel)))

(defn transform [{:keys [body id with-focus with-card with-callback]} form-str]
  (if (or with-focus with-card with-callback)
    (cond-> (read-string form-str)
            (some? with-focus)
            (add-focus with-focus body)
            (some? with-card)
            (add-card with-card id)
            (some? with-callback)
            (add-callback with-callback))
    form-str))

(defn clj-compiler-fn [example forms cb]
  (try
    (.send XhrIo
      "/eval"
      (fn [e]
        (if (.isSuccess (.-target e))
          (->> (.. e -target getResponseText)
               read-string
               rest ; ignore the result of in-ns
               (mapv #(if (vector? %) (into-array %) %))
               cb)
          (cb [])))
      "POST"
      (pr-str (into [(list 'in-ns (list 'quote (:ns-sym @*state)))]
                (mapv (partial transform (dissoc example :with-card)) forms))))
    (catch js/Error _ (cb []))))

(defn form->serializable
  "Converts the input to either a string or (if an error object) an array of data"
  [form]
  (if (instance? js/Error form)
    (array (or (some-> form .-cause .-message) (.-message form))
      (.-fileName form) (.-lineNumber form))
    (pr-str form)))

(defn cljs-compiler-fn [example forms cb]
  (es/code->results
    (into [(list 'ns (:ns-sym @*state))]
      (mapv (partial transform example) forms))
    (fn [results]
      (->> results
           rest ; ignore the result of ns
           (mapv form->serializable)
           cb))
    {:custom-load (fn [opts cb]
                    (cb {:lang :clj :source ""}))}))

(defn init-editor [elem]
  (when-let [paren-soup (or (.querySelector elem ".paren-soup") elem)]
    (ps/init paren-soup
      (js->clj {:compiler-fn (fn [])}))))

(defn init-example-editor [elem example]
  (when-let [paren-soup (or (.querySelector elem ".paren-soup") elem)]
    (when-let [content (.querySelector paren-soup ".content")]
      (set! (.-contentEditable content) true))
    (ps/init paren-soup
      (js->clj {:compiler-fn (if (= :clj (:type @*state))
                               (partial clj-compiler-fn example)
                               (partial cljs-compiler-fn example))}))))

(defn init-watcher! []
  (let [protocol (if (= (.-protocol js/location) "https:") "wss:" "ws:")
        host (-> js/window .-location .-host)
        sock (js/WebSocket. (str protocol "//" host "/watch"))]
    (set! (.-onopen sock)
      (fn [event]
        (.send sock js/window.location.pathname)))
    (set! (.-onmessage sock)
      (fn [event]
        (->> (.-data event)
             read-string
             (swap! *state merge))))
    sock))

(defn init []
  (swap! *state merge
    (-> (.querySelector js/document "#initial-state")
        .-textContent
        js/atob
        read-string))
  (rum/mount (common/app *state)
    (.querySelector js/document "#app"))
  (let [{:keys [watcher]} @*state]
    (swap! *state assoc
      :cljs-started? true
      :exportable? js/COMPILED
      :init-editor (memoize init-editor)
      :init-example-editor (memoize init-example-editor)
      :watcher (when-not js/COMPILED
                 (or watcher (init-watcher!)))))
  (when (:var-sym @*state)
    (doseq [button (-> js/document (.querySelectorAll ".button") array-seq)]
      (set! (.-display (.-style button)) "inline-block"))))

(init)

