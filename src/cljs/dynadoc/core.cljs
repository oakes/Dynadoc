(ns dynadoc.core
  (:require [cljs.reader :refer [read-string]]
            [rum.core :as rum]
            [dynadoc.common :as common]
            [paren-soup.core :as ps]))

(defonce state (atom {}))

(reset! state
  (-> (.querySelector js/document "#initial-state")
      .-textContent
      read-string))

(rum/mount (common/app state)
  (.querySelector js/document "#app"))

(swap! state assoc
  :toggle-tests
  #(swap! state update :tests? not)
  :toggle-instarepl
  #(swap! state update :instarepl? not))

(doseq [paren-soup (-> js/document (.querySelectorAll ".paren-soup") array-seq)]
  (ps/init paren-soup
    (js->clj {:compiler-fn (fn [])})))

(doseq [button (-> js/document (.querySelectorAll ".button") array-seq)]
  (set! (.-display (.-style button)) "inline-block"))

