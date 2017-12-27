(ns dynadoc.utils
  (:require [clojure.pprint :as pp]))

(defn form->serializable [form]
  (if (instance? Exception form)
    [(.getMessage form)]
    (pr-str form)))

(defn process-example [{:keys [body with-focus] :as example}]
  (assoc example
    :body-str
    (with-out-str
      (pp/pprint
        (or (:init-expr with-focus)
            body)))))

(defn flatten-vals [m]
  (reduce
    (fn [m [k v]]
      (update m k concat (vals v)))
    {}
    m))

