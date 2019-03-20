(ns dynadoc.transform
  (:require [clojure.walk :refer [postwalk]]
            [dynadoc.aliases] ))

(defn with-focus->binding [with-focus]
  (let [form (or (:form with-focus) ;; clojure 1.10
                 (:binding with-focus)) ;; clojure 1.9
        [binding-type binding-val] form]
    (when (#{:local-symbol ;; clojure 1.10
             :sym} ;; clojure 1.9
            binding-type)
      binding-val)))

(defn add-focus [form with-focus body]
  (if-let [bind (with-focus->binding with-focus)]
    (postwalk
      (fn [x]
        (if (= x bind)
          form
          x))
      body)
    form))

(defn add-card [form with-card id]
  (list 'let [with-card #?(:cljs (list '.getElementById 'js/document id) :clj id)] form))

(defn add-callback [form with-callback]
  (list 'let ['es-channel '(dynadoc.aliases/chan)
              with-callback '(fn [data]
                               (dynadoc.aliases/put! es-channel data))]
    form
    '(dynadoc.aliases/<!! es-channel)))

(defn transform
  ([example]
   (transform example (or (some-> example :with-focus :init-expr)
                          (:body example))))
  ([{:keys [body id with-focus with-card with-callback]} form]
   (cond-> form
           (some? with-focus)
           (add-focus with-focus body)
           (some? with-card)
           (add-card with-card id)
           (some? with-callback)
           (add-callback with-callback))))

