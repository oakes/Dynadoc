(ns dynadoc.core
  (:require [cljs.reader :refer [read-string]]
            [rum.core :as rum]
            [dynadoc.common :as common]
            [paren-soup.core :as ps])
  (:require-macros [dynadoc.example :refer [defexample]])
  (:import goog.net.XhrIo))

(defonce state (atom {}))

(reset! state
  (-> (.querySelector js/document "#initial-state")
      .-textContent
      read-string))

(rum/mount (common/app state)
  (.querySelector js/document "#app"))

(when (:var-sym @state)
  (doseq [button (-> js/document (.querySelectorAll ".button") array-seq)]
    (set! (.-display (.-style button)) "inline-block")))

(defn compiler-fn [forms cb]
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
      (pr-str (into [(str "(in-ns '" (:ns-sym @state) ")")]
                forms)))
    (catch js/Error _ (cb []))))

(defn init-paren-soup []
  (doseq [paren-soup (-> js/document (.querySelectorAll ".paren-soup") array-seq)]
    (when-let [edit (.querySelector paren-soup ".edit")]
      (set! (.-contentEditable edit) true))
    (ps/init paren-soup
      (js->clj {:compiler-fn compiler-fn}))))

(defexample init-paren-soup
  :doc "This is a test"
  :def (init-paren-soup))

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

