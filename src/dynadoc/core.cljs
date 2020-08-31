(ns dynadoc.core
  (:require [cljs.tools.reader :as r]
            [rum.core :as rum]
            [dynadoc.common :as common]
            [dynadoc.transform :as transform]
            [paren-soup.core :as ps]
            [eval-soup.core :as es]
            [goog.object :as gobj])
  (:import goog.net.XhrIo))

(defn read-string [s]
  (binding [r/*suppress-read* true]
    (r/read-string {:read-cond :preserve :eof nil} s)))

(defn transform [{:keys [with-focus with-card with-callback] :as ex} form-str]
  (if (or with-focus with-card with-callback)
    (transform/transform ex (read-string form-str))
    form-str))

(defn clj-compiler-fn [example forms cb]
  (let [ns-sym (:ns-sym (common/get-state))]
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
        (pr-str (into [(list 'in-ns (list 'quote ns-sym))]
                  (mapv (partial transform (dissoc example :with-card)) forms))))
      (catch js/Error _ (cb [])))))

(defn form->serializable
  "Converts the input to either a string or (if an error object) an array of data"
  [form]
  (if (instance? js/Error form)
    (array (or (some-> form .-cause .-message) (.-message form))
      (.-fileName form) (.-lineNumber form))
    (pr-str form)))

(defn cljs-compiler-fn [example forms cb]
  (let [ns-sym (:ns-sym (common/get-state))]
    (es/code->results
      (into [(list 'ns ns-sym)]
        (mapv (partial transform example) forms))
      (fn [results]
        (->> results
             rest ; ignore the result of ns
             (mapv form->serializable)
             cb))
      {:custom-load (fn [opts cb]
                      (cb {:lang :clj :source ""}))
       :disable-timeout? true})))

(defn init-editor [elem]
  (when-let [paren-soup (or (.querySelector elem ".paren-soup") elem)]
    (ps/init paren-soup
      (js->clj {:compiler-fn (fn [])}))))

(defn init-example-editor [elem example]
  (when-let [paren-soup (or (.querySelector elem ".paren-soup") elem)]
    (when-let [content (.querySelector paren-soup ".content")]
      (set! (.-contentEditable content) true))
    (let [type (:type (common/get-state))]
      (ps/init paren-soup
        (js->clj {:compiler-fn (if (= :clj type)
                                 (partial clj-compiler-fn example)
                                 (partial cljs-compiler-fn example))})))))

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
             (swap! common/*session common/update-session ::common/server))))
    sock))

(defn init []
  (->> (.querySelector js/document "#initial-state")
       .-textContent
       js/atob
       read-string
       (swap! common/*session common/update-session ::common/server))
  (rum/hydrate (common/app-root common/*session)
    (.querySelector js/document "#app"))
  (let [{:keys [var-sym watcher]} (common/get-state)]
    (->> {::common/cljs-started? true
          ::common/exportable? js/COMPILED
          ::common/init-editor (memoize init-editor)
          ::common/init-example-editor (memoize init-example-editor)
          ::common/watcher (when-not js/COMPILED
                             (or watcher (init-watcher!)))}
         (swap! common/*session common/update-session ::common/client))
    (when var-sym
      (doseq [button (-> js/document (.querySelectorAll ".button") array-seq)]
        (set! (.-display (.-style button)) "inline-block")))))

(init)

