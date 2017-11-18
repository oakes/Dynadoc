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

(doseq [paren-soup (-> js/document (.querySelectorAll ".paren-soup") array-seq)]
  (ps/init paren-soup
    #js {:compiler-fn (fn [])}))

