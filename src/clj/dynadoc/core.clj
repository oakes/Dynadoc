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
          (if (and current-ns
                   (list? form)
                   (contains? #{'def 'defn} (first form)))
            (let [[_ sym doc] form]
              (update ns->vars current-ns conj
                {:sym sym
                 :meta {:doc (when (string? doc)
                              doc)}
                 :source (with-out-str
                           (clojure.pprint/pprint
                             form))
                 :url (str "/cljs/" current-ns "/"
                        (java.net.URLEncoder/encode (str sym) "UTF-8"))}))
            ns->vars))
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
      ns->vars)))

(defn get-clj-nses []
  (map #(hash-map
          :sym (ns-name %)
          :type :clj
          :url (str "/clj/" %))
    (all-ns)))

(defn get-cljs-nses []
  (map #(hash-map
          :sym %
          :type :cljs
          :url (str "/cljs/" %))
    (keys (get-cljs-nses-and-vars))))

(defn get-nses []
  (let [clj-nses (get-clj-nses)
        cljs-nses (get-cljs-nses)]
    (->> (concat clj-nses cljs-nses)
         (sort-by :sym)
         vec)))

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

(defn get-cljs-vars [ns]
  (->> (get (get-cljs-nses-and-vars) ns)
       (sort-by :sym)
       vec))

(defn page [nses type ns-sym var-sym]
  (let [vars (case type
               clj (cond
                     var-sym [(get-clj-var-info ns-sym var-sym)]
                     ns-sym (get-clj-vars ns-sym))
               cljs (cond
                      var-sym [(some (fn [var]
                                       (when (-> var :sym (= var-sym))
                                         var))
                                 (get-cljs-vars ns-sym))]
                      ns-sym (get-cljs-vars ns-sym))
               nil)
        state (atom {:nses nses
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
         :body (page (get-nses) nil nil nil)})
      (let [[type ns var] (->> (str/split uri #"/")
                               (remove empty?)
                               (mapv #(-> % (java.net.URLDecoder/decode "UTF-8") symbol)))]
        (when (contains? #{'clj 'cljs} type)
          (let [nses (get-nses)]
            {:status 200
             :headers {"Content-Type" "text/html"}
             :body (page nses type ns var)})))
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

