(ns dynadoc.utils
  (:require [clojure.pprint :as pp]))

(def cli-options
  [["-p" "--port PORT" "Port number"
    :default 5000
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 % 0x10000) "Must be an integer between 0 and 65536"]]
   [nil "--host HOST" "The hostname that Dynadoc listens on"
    :default "0.0.0.0"]
   ["-u" "--usage" "Show CLI usage options"]])

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

