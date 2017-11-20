(ns dynadoc.core
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.repl :as repl]
            [ring.middleware.resource :refer [wrap-resource]]
            [ring.middleware.file :refer [wrap-file]]
            [ring.middleware.reload :refer [wrap-reload]]
            [ring.util.response :refer [redirect not-found]]
            [ring.util.request :refer [body-string]]
            [org.httpkit.server :refer [run-server]]
            [rum.core :as rum]
            [dynadoc.common :as common]
            [dynadoc.example :as ex]
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
                 (contains? #{'def 'defn} (first form)))
            (let [[_ sym & args] form]
              (update-in ns->vars [current-ns sym] merge
                {:sym sym
                 :meta {:doc (when (string? (first args))
                               (first args))
                        :arglists (when (= 'defn (first form))
                                    (get-cljs-arglists args))}
                 :source (with-out-str
                           (clojure.pprint/pprint
                             form))
                 :url (str "/cljs/" current-ns "/"
                        (java.net.URLEncoder/encode (str sym) "UTF-8"))}))
            (and current-ns
                 (list? form)
                 (symbol? (first form))
                 (= "defexample" (name (first form))))
            (let [[_ k & args] form
                  ns-sym (symbol (or (try (symbol (namespace k))
                                       (catch Exception _))
                                     current-ns))
                  k (cond
                      (symbol? k) (symbol (name k))
                      (keyword? k) (keyword (name k))
                      :else k)
                  examples (if (map? (first args)) args (list (apply hash-map args)))
                  examples (mapv (fn [example]
                                   (update example :def
                                     (fn [def]
                                       (with-out-str
                                         (clojure.pprint/pprint
                                           def)))))
                             examples)]
              (update-in ns->vars [ns-sym k] assoc
                :examples examples))
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
          (read-cljs-file ns->vars f))
        (recur (rest files) ns->vars))
      (reduce
        (fn [m [k v]]
          (update m k concat (vals v)))
        {}
        ns->vars))))

(defn get-clj-nses []
  (map #(hash-map
          :sym (ns-name %)
          :type :clj
          :url (str "/clj/" %))
    (all-ns)))

(defn get-cljs-nses [cljs-nses-and-vars]
  (map #(hash-map
          :sym %
          :type :cljs
          :url (str "/cljs/" %))
    (keys cljs-nses-and-vars)))

(defn get-clj-var-info [ns-sym var-sym]
  (let [sym (symbol (str ns-sym) (str var-sym))]
    {:sym var-sym
     :url (str "/clj/" ns-sym "/"
            (java.net.URLEncoder/encode (str var-sym) "UTF-8"))
     :meta (-> sym
               find-var
               meta
               (select-keys common/meta-keys))
     :source (repl/source-fn sym)
     :spec (try
             (require 'clojure.spec.alpha)
             (let [form (resolve (symbol "clojure.spec.alpha" "form"))]
               (with-out-str
                 (clojure.pprint/pprint
                   (form sym))))
             (catch Exception _))
     :examples (mapv (fn [example]
                       (update example :def
                         (fn [def]
                           (with-out-str
                             (clojure.pprint/pprint
                               def)))))
                 (get-in @ex/examples [ns-sym var-sym]))}))

(defn get-clj-vars [ns]
  (->> (ns-publics ns)
       keys
       (mapv (partial get-clj-var-info ns))
       (sort-by :sym)
       vec))

(defn get-cljs-vars [cljs-nses-and-vars ns]
  (->> (get cljs-nses-and-vars ns)
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

