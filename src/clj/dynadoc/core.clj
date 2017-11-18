(ns dynadoc.core
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.repl :as repl]
            [ring.middleware.resource :refer [wrap-resource]]
            [ring.middleware.file :refer [wrap-file]]
            [ring.middleware.reload :refer [wrap-reload]]
            [ring.util.response :refer [redirect not-found]]
            [ring.util.request :refer [body-string]]
            [org.httpkit.server :refer [run-server]]
            [rum.core :as rum]
            [dynadoc.common :as common]))

(defonce web-server (atom nil))
(defonce options (atom nil))

(defn get-nses []
  (->> (all-ns) (map ns-name) sort vec))

(defn get-vars [ns]
  (->> (ns-publics ns) keys sort vec))

(defn page [nses ns-sym var-sym]
  (let [vars (cond
               var-sym [var-sym]
               ns-sym (get-vars ns-sym))
        vars (mapv (fn [var-sym]
                     (let [sym (symbol (str ns-sym) (str var-sym))]
                       {:sym var-sym
                        :url (str "/" ns-sym "/"
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
                                (catch Exception _))}))
               vars)
        state (atom {:nses nses :ns-sym ns-sym :var-sym var-sym :vars vars})]
    (-> "template.html" io/resource slurp
        (str/replace "{{content}}" (rum/render-html (common/app state)))
        (str/replace "{{initial-state}}" (pr-str @state)))))

(defn handler [request]
  (or (when (= (:uri request) "/")
        {:status 200
         :headers {"Content-Type" "text/html"}
         :body (page (get-nses) nil nil)})
      (let [nses (get-nses)
            [ns var] (->> (str/split (:uri request) #"/")
                          (remove empty?)
                          (mapv #(-> % (java.net.URLDecoder/decode "UTF-8") symbol)))]
        (when (contains? (set nses) ns)
          {:status 200
           :headers {"Content-Type" "text/html"}
           :body (page nses ns var)}))
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

