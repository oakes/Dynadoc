(ns dynadoc.core
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.repl :as repl]
            [clojure.walk :as walk]
            [ring.middleware.resource :refer [wrap-resource]]
            [ring.middleware.file :refer [wrap-file]]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.util.response :refer [not-found]]
            [ring.util.request :refer [body-string]]
            [org.httpkit.server :refer [run-server send!]]
            [rum.core :as rum]
            [dynadoc.common :as common]
            [dynadoc.static :as static]
            [dynadoc.utils :as u]
            [dynadoc.example :as ex]
            [dynadoc.watch :as watch]
            [dynadoc.aliases]
            [eval-soup.core :as es]
            [clojure.tools.cli :as cli]
            [clojure.data.codec.base64 :as base64])
  (:import [java.util.zip ZipEntry ZipOutputStream]))

(defonce *web-server (atom nil))
(defonce *options (atom nil))

(defn exclude-var? [ns-sym var-sym]
  (or (str/includes? (name var-sym) ".proxy$")
      (contains? (:exclusions @*options)
                 (symbol (name ns-sym) (name var-sym)))))

(defn get-examples
  ([ns-sym var-sym]
   (let [examples (get-in @ex/registry-ref [ns-sym var-sym])]
     (vec
       (for [i (range (count examples))]
         (-> (get examples i)
             u/process-example
             (assoc :id (str ns-sym "/" var-sym "/" i)))))))
  ([ns-sym]
   (reduce
     (fn [m [k v]]
       (assoc m k {:sym k
                   :examples (vec
                               (for [i (range (count v))]
                                 (-> (get v i)
                                     u/process-example
                                     (assoc :id (str ns-sym "/" k "/" i)))))}))
     {}
     (get @ex/registry-ref ns-sym))))

(defn var-map->vars [ns-sym var-map]
  (if (empty? var-map)
    []
    (->> (merge (get-examples ns-sym) var-map)
         vals
         (sort-by #(-> % :sym str))
         vec)))

(defn get-cljs-nses-and-vars-dynamically []
  (when-let [*env (:*cljs-env @*options)]
    (require 'cljs.analyzer.api)
    (let [all-ns (resolve (symbol "cljs.analyzer.api" "all-ns"))
          ns-publics (resolve (symbol "cljs.analyzer.api" "ns-publics"))]
       (reduce
         (fn [m ns-sym]
           (let [var-map (reduce
                           (fn [m [var-sym {:keys [doc arglists anonymous] :as parsed-var}]]
                             (if anonymous
                               m
                               (assoc m var-sym
                                 {:sym var-sym
                                  :meta {:doc doc
                                         :arglists (if (= 'quote (first arglists))
                                                     (second arglists)
                                                     arglists)}
                                  :examples (get-examples ns-sym var-sym)
                                  :methods (some-> parsed-var
                                                   :protocol-info
                                                   :methods
                                                   keys
                                                   sort)
                                  :protocol (some-> parsed-var
                                                    :protocol
                                                    name
                                                    symbol)})))
                           {}
                           (ns-publics *env ns-sym))
                 vars (var-map->vars ns-sym var-map)]
             (assoc m ns-sym vars)))
         {}
         (all-ns *env)))))

(defn get-cljs-nses-and-vars []
  (or (get-cljs-nses-and-vars-dynamically)
      (->> (static/get-cljs-nses-and-vars)
           (reset! watch/*cljs-info)
           u/flatten-vals)))

(defn get-cljs-nses [cljs-nses-and-vars]
  (->> (keys cljs-nses-and-vars)
       (map #(hash-map
               :sym %
               :type :cljs
               :var-syms (mapv :sym (get cljs-nses-and-vars %))))
       (remove #(-> % :var-syms empty?))))

(defn get-cljs-vars [cljs-nses-and-vars ns]
  (->> (get cljs-nses-and-vars ns)
       (remove #(exclude-var? ns (:sym %)))
       (sort-by #(-> % :sym str))
       vec))

(defn get-clj-nses []
  (->> (all-ns)
       (map #(hash-map
               :sym (ns-name %)
               :type :clj
               :var-syms (vec (keys (ns-publics %)))))
       (remove #(-> % :var-syms empty?))))

(defn get-clj-var-info [ns-sym var-sym]
  (let [sym (symbol (str ns-sym) (str var-sym))]
    {:sym var-sym
     :meta (-> sym
               find-var
               meta
               (select-keys [:arglists :doc]))
     :source (try (repl/source-fn sym)
               (catch Exception _))
     :spec (try
             (require 'clojure.spec.alpha)
             (let [form (resolve (symbol "clojure.spec.alpha" "form"))]
               (with-out-str
                 (clojure.pprint/pprint
                   (form sym))))
             (catch Exception _))
     :examples (get-examples ns-sym var-sym)}))

(defn get-clj-vars [ns-sym]
  (->> (ns-publics ns-sym)
       keys
       (reduce
         (fn [m var-sym]
           (let [var-info (get-clj-var-info ns-sym var-sym)]
             (if (exclude-var? ns-sym var-sym)
               m
               (assoc m var-sym var-info))))
         {})
       (var-map->vars ns-sym)))

(defn dedupe-nses [pref nses]
  (reduce-kv
    (fn [nses sym nses-for-sym]
      (conj nses
            (or (first (filter #(= pref (:type %)) nses-for-sym))
                (first nses-for-sym))))
    []
  (group-by :sym nses)))

(defn page-state [uri {:keys [::common/type ::common/ns-sym ::common/var-sym ::common/static? ::common/nses ::common/cljs-nses-and-vars] :as opts}]
  (let [cljs-nses-and-vars (or cljs-nses-and-vars (get-cljs-nses-and-vars))
        dedupe-pref (:dedupe-pref @*options)
        dedupe-fn (if dedupe-pref
                    (partial dedupe-nses dedupe-pref)
                    identity)
        nses (or nses
                 (->> (concat (get-clj-nses) (get-cljs-nses cljs-nses-and-vars))
                      dedupe-fn
                      (sort-by #(-> % :sym str))
                      vec))
        vars (case type
               :clj (cond
                      var-sym [(get-clj-var-info ns-sym var-sym)]
                      ns-sym (get-clj-vars ns-sym))
               :cljs (cond
                       var-sym [(some (fn [var]
                                        (when (-> var :sym (= var-sym))
                                          var))
                                  (get-cljs-vars cljs-nses-and-vars ns-sym))]
                       ns-sym (get-cljs-vars cljs-nses-and-vars ns-sym))
               nil)
        rel-path (-> (remove empty? (str/split uri #"/"))
                     count
                     (- 1)
                     (repeat "../")
                     str/join)]
      (merge
        opts
        {::common/static? static?
         ::common/nses nses
         ::common/ns-meta (when (= type :clj)
                     (some-> ns-sym the-ns meta))
         ::common/vars vars
         ::common/rel-path rel-path
         ::common/hide-badge? (some? dedupe-pref)})))

(defn page [uri opts]
  (let [state (page-state uri opts)]
    (-> "template.html" io/resource slurp
        (str/replace "{{rel-path}}" (::common/rel-path state))
        (str/replace "{{content}}" (rum/render-html (common/app (atom state))))
        (str/replace "{{initial-state}}" (-> (pr-str state)
                                             (.getBytes "UTF-8")
                                             base64/encode
                                             (String. "UTF-8"))))))

(def public-files
  ["main.js"
   "paren-soup-dark.css"
   "paren-soup-light.css"
   "style.css"
   "fonts/FiraCode-Bold.otf"
   "fonts/FiraCode-Light.otf"
   "fonts/FiraCode-Medium.otf"
   "fonts/FiraCode-Regular.otf"
   "fonts/FiraCode-Retina.otf"
   "fonts/glyphicons-halflings-regular.eot"
   "fonts/glyphicons-halflings-regular.svg"
   "fonts/glyphicons-halflings-regular.ttf"
   "fonts/glyphicons-halflings-regular.woff"
   "fonts/glyphicons-halflings-regular.woff2"])

(defn export [{:strs [pages export-filter type ns-sym var-sym]}]
  (let [type (some-> type keyword)
        ns-sym (some-> ns-sym symbol)
        var-sym (when var-sym
                  (edn/read-string var-sym))
        zip-file (io/file "dynadoc-export.zip")]
    (with-open [zip (ZipOutputStream. (io/output-stream zip-file))]
      (case pages
        "single"
        (cond
          (some? var-sym)
          (do
            (.putNextEntry zip (ZipEntry. "index.html"))
            (io/copy (page
                       "/index.html"
                       {::common/type type
                        ::common/ns-sym ns-sym
                        ::common/var-sym var-sym
                        ::common/static? true
                        ::common/hide-sidebar? true})
              zip)
            (.closeEntry zip))
          (some? ns-sym)
          (let [cljs-nses-and-vars (get-cljs-nses-and-vars)
                var-syms (map :sym
                           (case type
                             :cljs (get-cljs-vars cljs-nses-and-vars ns-sym)
                             :clj (get-clj-vars ns-sym)))]
            (.putNextEntry zip (ZipEntry. "index.html"))
            (io/copy (page
                       "/index.html"
                       {::common/type type
                        ::common/ns-sym ns-sym
                        ::common/static? true
                        ::common/hide-sidebar? true
                        ::common/cljs-nses-and-vars cljs-nses-and-vars})
              zip)
            (.closeEntry zip)
            (doseq [var-sym var-syms
                    :let [path (common/var-sym->url "" true type ns-sym var-sym)]]
              (.putNextEntry zip (ZipEntry. path))
              (io/copy (page path
                         {::common/type type
                          ::common/ns-sym ns-sym
                          ::common/var-sym var-sym
                          ::common/static? true
                          ::common/hide-sidebar? true
                          ::common/cljs-nses-and-vars cljs-nses-and-vars})
                zip)
              (.closeEntry zip))))
        "multiple"
        (let [clj-nses (get-clj-nses)
              cljs-nses-and-vars (get-cljs-nses-and-vars)
              cljs-nses (get-cljs-nses cljs-nses-and-vars)
              nses (if-let [search (some-> export-filter re-pattern)]
                     (filter #(re-find search (-> % :sym str))
                             (concat clj-nses cljs-nses))
                     (concat clj-nses cljs-nses))
              dedupe-pref (:dedupe-pref @*options)
              nses (cond->> nses dedupe-pref (dedupe-nses dedupe-pref))
              nses (sort-by :sym nses)]
          (.putNextEntry zip (ZipEntry. "index.html"))
          (io/copy (page "/index.html"
                     {::common/static? true
                      ::common/cljs-nses-and-vars cljs-nses-and-vars
                      ::common/nses nses})
            zip)
          (.closeEntry zip)
          (doseq [{:keys [sym type var-syms]} nses
                  :let [path (str (name type) "/" sym ".html")]]
            (.putNextEntry zip (ZipEntry. path))
            (io/copy (page path
                       {::common/type type
                        ::common/ns-sym sym
                        ::common/static? true
                        ::common/cljs-nses-and-vars cljs-nses-and-vars
                        ::common/nses nses})
              zip)
            (.closeEntry zip)
            (doseq [var-sym var-syms
                    :let [path (common/var-sym->url "" true type sym var-sym)]]
              (.putNextEntry zip (ZipEntry. path))
              (io/copy (page path
                         {::common/type type
                          ::common/ns-sym sym
                          ::common/var-sym var-sym
                          ::common/static? true
                          ::common/cljs-nses-and-vars cljs-nses-and-vars
                          ::common/nses nses})
                zip)
              (.closeEntry zip)))))
      (doseq [path public-files]
        (.putNextEntry zip (ZipEntry. path))
        (io/copy
          (io/input-stream
            (or (io/resource (str "dynadoc-extend/" path))
                (io/resource (str "dynadoc-public/" path))))
          zip)
        (.closeEntry zip)))
    zip-file))

(defn handler [{:keys [uri] :as request}]
  (or (case uri
        "/"
        {:status 200
         :headers {"Content-Type" "text/html"}
         :body (page uri {::common/static? false})}
        "/eval"
        {:status 200
         :headers {"Content-Type" "text/plain"}
         :body (->> request
                    :body
                    .bytes
                    slurp
                    edn/read-string
                    (#(es/code->results % {:disable-security? true}))
                    (mapv u/form->serializable)
                    pr-str)}
        "/dynadoc-export.zip"
        {:status 200
         :headers {"Content-Type" "application/zip"}
         :body (export (:params request))}
        "/watch"
        (watch/watch-request request)
        nil)
      (let [[type ns-sym var-sym] (u/parse-uri uri)]
        (when (contains? #{:clj :cljs} type)
          {:status 200
           :headers {"Content-Type" "text/html"}
           :body (page uri {::common/static? false
                            ::common/type type
                            ::common/ns-sym ns-sym
                            ::common/var-sym var-sym})}))
      (not-found "Page not found")))

(defn print-server [server]
  (println
    (str "Started Dynadoc on http://localhost:"
      (-> server meta :local-port)))
  server)

(defn start
  ([opts]
   (-> handler
       (wrap-resource "dynadoc-public")
       (wrap-resource "dynadoc-extend")
       (start opts)))
  ([app opts]
   (when-not @*web-server
     ; start watcher if parsing cljs statically
     (when-not (:*cljs-env opts)
       (add-watch watch/*cljs-info :cljs-info
         (fn [_ _ _ cljs-info]
           (doseq [[channel uri] @watch/*channel->uri]
             (let [[type ns-sym var-sym] (u/parse-uri uri)]
               (->> {::common/static? false
                     ::common/cljs-nses-and-vars (u/flatten-vals cljs-info)
                     ::common/type type
                     ::common/ns-sym ns-sym
                     ::common/var-sym var-sym}
                    (page-state uri)
                    pr-str
                    (send! channel))))))
       (watch/init-watcher!))
     ; start server
     (->> (merge {:port 0} opts)
          (reset! *options)
          (run-server (-> app wrap-content-type wrap-params wrap-keyword-params))
          (reset! *web-server)
          print-server))))

(defn dev-start [opts]
  (when-not @*web-server
    (.mkdirs (io/file "target" "dynadoc-public"))
    (start (-> handler
               (wrap-resource "dynadoc-public")
               (wrap-file "target/dynadoc-public"))
      opts)))

(defn -main [& args]
  (let [cli (cli/parse-opts args u/cli-options)]
    (cond
      ;; if there are CLI errors, print error messages and usage summary
      (:errors cli)
      (println (:errors cli) "\n" (:summary cli))
      ;; if user asked for CLI usage, print the usage summary
      (get-in cli [:options :usage])
      (println (:summary cli))
      ;; in other cases start Dynadoc
      :otherwise
      (start (:options cli)))))

