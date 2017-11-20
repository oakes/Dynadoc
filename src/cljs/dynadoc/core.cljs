(ns dynadoc.core
  (:require [cljs.reader :refer [read-string]]
            [rum.core :as rum]
            [dynadoc.common :as common]
            [paren-soup.core :as ps]
            [eval-soup.core :as es]
            [goog.object :as gobj])
  (:import goog.net.XhrIo))

(defonce state (atom {}))

(defn clj-compiler-fn [forms cb]
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
      (pr-str (into [(str "(in-ns '" (:ns-sym @state) ")")] forms)))
    (catch js/Error _ (cb []))))

(defn cljs-compiler-fn [forms cb]
  (let [iframe (.querySelector js/document "#cljsapp")
        forms (cons (str "(ns " (:ns-sym @state) ")")
                forms)]
    (.addEventListener js/window "message"
      (fn on-message [e]
        (let [data (.-data e)]
          (-> (gobj/get data "results")
              (.slice 1) ; ignore the result of ns
              cb))
        (.removeEventListener js/window "message" on-message)))
    (.postMessage (.-contentWindow iframe)
      (clj->js {:type "instarepl" :forms (into-array forms)})
      "*")))

(defn init-paren-soup []
  (doseq [paren-soup (-> js/document (.querySelectorAll ".paren-soup") array-seq)]
    (when-let [edit (.querySelector paren-soup ".edit")]
      (set! (.-contentEditable edit) true))
    (ps/init paren-soup
      (js->clj {:compiler-fn (if (= :clj (:type @state))
                               clj-compiler-fn
                               cljs-compiler-fn)}))))

(defn toggle-instarepl [show?]
  (let [instarepls (-> js/document
                       (.querySelectorAll ".instarepl")
                       array-seq)]
    (if show?
      (doseq [ir instarepls]
        (-> ir .-style .-display (set! "list-item")))
      (doseq [ir instarepls]
        (-> ir .-style .-display (set! "none")))))
  (init-paren-soup))

(defn init []
  (reset! state
    (-> (.querySelector js/document "#initial-state")
        .-textContent
        read-string))
  (rum/mount (common/app state)
    (.querySelector js/document "#app"))
  (when (:var-sym @state)
    (doseq [button (-> js/document (.querySelectorAll ".button") array-seq)]
      (set! (.-display (.-style button)) "inline-block")))
  (swap! state assoc :toggle-instarepl toggle-instarepl)
  (init-paren-soup))

(defn init-prod []
  (init)
  (swap! state assoc :disable-cljs-instarepl? true))

