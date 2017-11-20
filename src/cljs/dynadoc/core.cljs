(ns dynadoc.core
  (:require [cljs.reader :refer [read-string]]
            [rum.core :as rum]
            [dynadoc.common :as common]
            [paren-soup.core :as ps]
            [eval-soup.core :as es])
  (:require-macros [dynadoc.example :refer [defexample]])
  (:import goog.net.XhrIo))

(defonce state (atom {}))

(reset! state
  (-> (.querySelector js/document "#initial-state")
      .-textContent
      read-string))

(rum/mount (common/app state)
  (.querySelector js/document "#app"))

(defn disable-cljs-instarepl []
  (swap! state assoc :disable-cljs-instarepl? true))

(when (:var-sym @state)
  (doseq [button (-> js/document (.querySelectorAll ".button") array-seq)]
    (set! (.-display (.-style button)) "inline-block")))

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

(defn form->serializable [form]
  (if (instance? js/Error form)
    (array (or (some-> form .-cause .-message) (.-message form))
      (.-fileName form) (.-lineNumber form))
    (pr-str form)))

(defn cljs-compiler-fn [forms cb]
  (es/code->results
    (into [(str "(ns " (:ns-sym @state) ")")] forms)
    (fn [results]
      (->> results
           rest ; ignore the result of ns
           (mapv form->serializable)
           cb))
    {:custom-load (fn [opts cb]
                    (cb {:lang :clj :source ""}))}))

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

(swap! state assoc :toggle-instarepl toggle-instarepl)

(init-paren-soup)

