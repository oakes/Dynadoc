(ns dynadoc.static
  (:require [dynadoc.utils :as u]
            [dynadoc.example :as ex]
            [clojure.java.io :as io]
            [clojure.tools.reader :as r]
            [clojure.tools.reader.reader-types :refer [indexing-push-back-reader]]))

(defn get-cljs-arglists [args]
  (loop [args args
         arglists []]
    (if-let [arg (first args)]
      (cond
        (vector? arg)
        (list arg)
        (and (list? arg) (vector? (first arg)))
        (recur (rest args) (conj arglists (first arg)))
        :else
        (recur (rest args) arglists))
      arglists)))

(defn read-cljs-file [ns->vars f]
  (let [reader (indexing-push-back-reader (slurp f))]
    (loop [current-ns nil
           ns->vars ns->vars]
      (if-let [form (try
                      (binding [r/*suppress-read* true]
                        (r/read {:read-cond :preserve :eof nil} reader))
                      (catch Exception _ '(comment "Reader error")))]
        (recur
          (if (and (list? form)
                   (= (first form) 'ns))
            (second form)
            current-ns)
          (cond
            
            ; functions
            (and current-ns
                 (list? form)
                 (contains? #{'def 'defn 'defonce} (first form))
                 (>= (count form) 3)
                 (not (some-> form second meta :private)))
            (let [[call sym & args] form]
              (update-in ns->vars [current-ns sym] merge
                {:sym sym
                 :meta (merge
                         (when (= call 'defn)
                           {:doc (when (string? (first args))
                                   (first args))
                            :arglists (get-cljs-arglists args)})
                         (select-keys (meta sym) [:arglists :doc]))
                 :source (with-out-str
                           (clojure.pprint/pprint
                             form))}))
            
            ; examples
            (and current-ns
                 (list? form)
                 (symbol? (first form))
                 (contains? #{'defexample 'defexamples} (-> form first name symbol)))
            (let [[sym k & args] form
                  sym (-> sym name symbol)
                  ns-sym (or (ex/parse-ns k) current-ns)
                  var-sym (ex/parse-val k)
                  examples (case sym
                             defexample [(ex/parse-example args)]
                             defexamples (mapv ex/parse-example args))
                  examples (vec
                             (for [i (range (count examples))]
                               (-> (get examples i)
                                   u/process-example
                                   (assoc :id (str ns-sym "/" var-sym "/" i)))))]
              (update-in ns->vars [ns-sym var-sym] merge
                {:sym var-sym
                 :examples examples}))
            
            ; protocols
            (and current-ns
                 (list? form)
                 (= 'defprotocol (first form))
                 (symbol? (second form)))
            (let [[call var-sym] form
                  methods (reduce
                            (fn [methods sub-form]
                              (if (list? sub-form)
                                (conj methods
                                  {:sym (first sub-form)
                                   :meta {:doc (first (filter string? sub-form))
                                          :arglists (filter vector? sub-form)}
                                   :protocol var-sym})
                                methods))
                            []
                            form)
                  protocol {:sym var-sym
                            :meta {:doc (first (filter string? form))}
                            :methods (sort (map :sym methods))}]
              (reduce
                (fn [ns->vars {:keys [sym] :as parsed-var}]
                  (update-in ns->vars [current-ns sym] merge parsed-var))
                ns->vars
                (conj methods protocol)))
            
            ; else
            :else ns->vars))
        ns->vars))))

(defn visible? [^java.io.File f]
  (let [n (.getName f)]
    (or (= n ".")
        (not (.startsWith n ".")))))

(defn get-cljs-nses-and-vars []
  (loop [files (tree-seq
                 (fn [^java.io.File f]
                   (and (.isDirectory f) (visible? f)))
                 (fn [^java.io.File d]
                   (seq (.listFiles d)))
                 (io/file "."))
         ns->vars {}]
    (if-let [f (first files)]
      (if (and (.isFile f)
               (or (-> f .getName (.endsWith ".cljs"))
                   (-> f .getName (.endsWith ".cljc"))))
        (recur
          (rest files)
          (try
            (read-cljs-file ns->vars f)
            (catch Exception e
              (.printStackTrace e)
              ns->vars)))
        (recur (rest files) ns->vars))
      ns->vars)))

