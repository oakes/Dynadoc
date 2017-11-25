(ns dynadoc.core
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.repl :as repl]
            [clojure.walk :as walk]
            [ring.middleware.resource :refer [wrap-resource]]
            [ring.middleware.file :refer [wrap-file]]
            [ring.middleware.reload :refer [wrap-reload]]
            [ring.util.response :refer [redirect not-found]]
            [ring.util.request :refer [body-string]]
            [org.httpkit.server :refer [run-server]]
            [rum.core :as rum]
            [dynadoc.common :as common]
            [eval-soup.core :as es]
            [clojure.tools.reader :as r]
            [clojure.tools.reader.reader-types :refer [indexing-push-back-reader]]))

(defonce web-server (atom nil))
(defonce options (atom nil))

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

(defn process-example [{:keys [body with-focus] :as example}]
  (assoc example
    :body-str
    (with-out-str
      (clojure.pprint/pprint
        (or (:init-expr with-focus)
            body)))))

(defn read-cljs-file [ns->vars f]
  (let [reader (indexing-push-back-reader (slurp f))]
    (loop [current-ns nil
           ns->vars ns->vars]
      (if-let [form (try (r/read {:eof nil} reader)
                      (catch Exception _ '(comment "Reader error")))]
        (recur
          (if (and (list? form)
                   (= (first form) 'ns))
            (second form)
            current-ns)
          (cond
            (and current-ns
                 (list? form)
                 (contains? #{'def 'defn 'defonce} (first form))
                 (not (some-> form second meta :private)))
            (let [[call sym & args] form]
              (update-in ns->vars [current-ns sym] merge
                {:sym sym
                 :meta (merge
                         (when (= call 'defn)
                           {:doc (when (string? (first args))
                                   (first args))
                            :arglists (get-cljs-arglists args)})
                         (select-keys (meta sym) common/meta-keys))
                 :source (with-out-str
                           (clojure.pprint/pprint
                             form))
                 :url (str "/cljs/" current-ns "/"
                        (java.net.URLEncoder/encode (str sym) "UTF-8"))}))
            (and current-ns
                 (list? form)
                 (symbol? (first form))
                 (contains? #{'defexample 'defexamples} (-> form first name symbol)))
            (try
              (require 'dynadoc.example)
              (let [parse-ns (resolve (symbol "dynadoc.example" "parse-ns"))
                    parse-val (resolve (symbol "dynadoc.example" "parse-val"))
                    parse-example (resolve (symbol "dynadoc.example" "parse-example"))
                    [sym k & args] form
                    sym (-> sym name symbol)
                    ns-sym (or (parse-ns k) current-ns)
                    var-sym (parse-val k)
                    examples (case sym
                               defexample [(parse-example args)]
                               defexamples (mapv parse-example args))
                    examples (vec
                               (for [i (range (count examples))]
                                 (-> (get examples i)
                                     process-example
                                     (assoc :id (str ns-sym "/" var-sym "/" i)))))]
                (update-in ns->vars [ns-sym var-sym] assoc
                  :examples examples))
              (catch Exception _ ns->vars))
            :else ns->vars))
        ns->vars))))

(defn get-cljs-nses-and-vars []
  (loop [files (file-seq (io/file "."))
         ns->vars {}]
    (if-let [f (first files)]
      (if (and (.isFile f)
               (-> f .getName (.endsWith ".cljs")))
        (recur
          (rest files)
          (try
            (read-cljs-file ns->vars f)
            (catch Exception e
              (.printStackTrace e)
              ns->vars)))
        (recur (rest files) ns->vars))
      (reduce
        (fn [m [k v]]
          (update m k concat (vals v)))
        {}
        ns->vars))))

(defn get-cljs-nses [cljs-nses-and-vars]
  (map #(hash-map
          :sym %
          :type :cljs
          :url (str "/cljs/" %)
          :var-syms (mapv :sym (get cljs-nses-and-vars %)))
    (keys cljs-nses-and-vars)))

(defn get-cljs-vars [cljs-nses-and-vars ns]
  (->> (get cljs-nses-and-vars ns)
       (sort-by :sym)
       vec))

(defn get-clj-nses []
  (map #(hash-map
          :sym (ns-name %)
          :type :clj
          :url (str "/clj/" %)
          :var-syms (vec (keys (ns-publics %))))
    (all-ns)))

(defn get-clj-var-info [ns-sym var-sym]
  (let [sym (symbol (str ns-sym) (str var-sym))]
    {:sym var-sym
     :url (str "/clj/" ns-sym "/"
            (java.net.URLEncoder/encode (str var-sym) "UTF-8"))
     :meta (-> sym
               find-var
               meta
               (select-keys common/meta-keys))
     :source (try (repl/source-fn sym)
               (catch Exception _))
     :spec (try
             (require 'clojure.spec.alpha)
             (let [form (resolve (symbol "clojure.spec.alpha" "form"))]
               (with-out-str
                 (clojure.pprint/pprint
                   (form sym))))
             (catch Exception _))
     :examples (try
                 (require 'dynadoc.example)
                 (let [registry-ref (var-get (resolve (symbol "dynadoc.example" "registry-ref")))
                       examples (get-in @registry-ref [ns-sym var-sym])]
                   (vec
                     (for [i (range (count examples))]
                       (-> (get examples i)
                           process-example
                           (assoc :id (str ns-sym "/" var-sym "/" i))))))
                 (catch Exception _))}))

(defn get-clj-vars [ns]
  (->> (ns-publics ns)
       keys
       (mapv (partial get-clj-var-info ns))
       (sort-by :sym)
       vec))

(defn page [type ns-sym var-sym]
  (let [clj-nses (get-clj-nses)
        cljs-nses-and-vars (get-cljs-nses-and-vars)
        cljs-nses (get-cljs-nses cljs-nses-and-vars)
        nses (->> (concat clj-nses cljs-nses)
                  (sort-by :sym)
                  vec)
        vars (case type
               clj (cond
                     var-sym [(get-clj-var-info ns-sym var-sym)]
                     ns-sym (get-clj-vars ns-sym))
               cljs (cond
                      var-sym [(some (fn [var]
                                       (when (-> var :sym (= var-sym))
                                         var))
                                 (get-cljs-vars cljs-nses-and-vars ns-sym))]
                      ns-sym (get-cljs-vars cljs-nses-and-vars ns-sym))
               nil)
        state (atom {:type (some-> type name keyword)
                     :nses nses
                     :ns-sym ns-sym
                     :ns-meta (when (= type 'clj)
                                (some-> ns-sym the-ns meta))
                     :var-sym var-sym
                     :vars vars})]
    (-> "template.html" io/resource slurp
        (str/replace "{{content}}" (rum/render-html (common/app state)))
        (str/replace "{{initial-state}}" (pr-str @state)))))

(defn form->serializable [form]
  (if (instance? Exception form)
    [(.getMessage form)]
    (pr-str form)))

(defn handler [{:keys [uri] :as request}]
  (or (when (= uri "/")
        {:status 200
         :headers {"Content-Type" "text/html"}
         :body (page nil nil nil)})
      (let [[type ns var] (->> (str/split uri #"/")
                               (remove empty?)
                               (mapv #(-> % (java.net.URLDecoder/decode "UTF-8") symbol)))]
        (when (contains? #{'clj 'cljs} type)
          {:status 200
           :headers {"Content-Type" "text/html"}
           :body (page type ns var)}))
      (when (= uri "/eval")
        {:status 200
         :headers {"Content-Type" "text/plain"}
         :body (->> request
                    body-string
                    edn/read-string
                    es/code->results
                    (mapv form->serializable)
                    pr-str)})
      (not-found "Page not found")))

(defn print-server [server]
  (println
    (str "Started Dynadoc on http://localhost:"
      (-> server meta :local-port)))
  server)

(defn start
  ([opts]
   (start (wrap-resource handler "dynadoc-public") opts))
  ([app opts]
   (when-not @web-server
     (->> (merge {:port 0} opts)
          (reset! options)
          (run-server app)
          (reset! web-server)
          print-server))))

(defn dev-start [opts]
  (when-not @web-server
    (.mkdirs (io/file "target" "dynadoc-public"))
    (start (-> #'handler
               (wrap-reload)
               (wrap-file "target/dynadoc-public"))
      opts)))

