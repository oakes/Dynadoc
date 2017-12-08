(ns dynadoc.core
  (:require [cljs.reader :refer [read-string]]
            [rum.core :as rum]
            [dynadoc.common :as common]
            [dynadoc.state :refer [*state]]
            [paren-soup.core :as ps]
            [eval-soup.core :as es]
            [goog.object :as gobj]
            [clojure.walk :refer [postwalk]]
            [goog.object :as gobj])
  (:import goog.net.XhrIo))

(def ^:const version "1.1.5")
(def ^:const api-url "https://clojars.org/api/artifacts/dynadoc")

(defn with-focus->binding [with-focus]
  (let [{:keys [binding]} with-focus
        [binding-type binding-val] binding]
    (when (= :sym binding-type)
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

(defn transform [{:keys [body id with-focus with-card]} form-str]
  (if (or with-focus with-card)
    (pr-str
      (cond-> (read-string form-str)
              (some? with-focus)
              (add-focus with-focus body)
              (some? with-card)
              (add-card with-card id)))
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
      (pr-str (into [(str "(in-ns '" (:ns-sym @*state) ")")]
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
    (into [(str "(ns " (:ns-sym @*state) ")")]
      (mapv (partial transform example) forms))
    (fn [results]
      (->> results
           rest ; ignore the result of ns
           (mapv form->serializable)
           cb))
    {:custom-load (fn [opts cb]
                    (cb {:lang :clj :source ""}))}))

(defn init-paren-soup []
  (let [examples (->> @*state :vars (mapcat :examples) vec)
        editors (-> js/document (.querySelectorAll ".example") array-seq vec)]
    (dotimes [i (count editors)]
      (let [paren-soup (get editors i)
            example (get examples i)]
        (when-let [content (.querySelector paren-soup ".content")]
          (set! (.-contentEditable content) true))
        (ps/init paren-soup
          (js->clj {:compiler-fn (if (= :clj (:type @*state))
                                   (partial clj-compiler-fn example)
                                   (partial cljs-compiler-fn example))})))))
  (doseq [paren-soup (-> js/document (.querySelectorAll ".nonedit") array-seq vec)]
    (ps/init paren-soup
      (js->clj {:compiler-fn (fn [])}))))

(defn prod []
  (swap! *state assoc :prod? true))

(defn check-version []
  (.send XhrIo
    api-url
    (fn [e]
      (when (and (.isSuccess (.-target e))
                 (->> (.. e -target getResponseText)
                      (.parse js/JSON)
                      (#(gobj/get % "latest_release"))
                      (not= version)))
        (swap! *state assoc :update? true)))
    "GET"))

(defn init []
  (swap! *state merge
    (-> (.querySelector js/document "#initial-state")
        .-textContent
        read-string))
  (rum/mount (common/app *state)
    (.querySelector js/document "#app"))
  (let [{:keys [static? dev?]} @*state]
    (when (and (not static?) (not dev?))
      (check-version)))
  (swap! *state assoc :cljs-started? true)
  (when (:var-sym @*state)
    (doseq [button (-> js/document (.querySelectorAll ".button") array-seq)]
      (set! (.-display (.-style button)) "inline-block")))
  (init-paren-soup))

(init)

