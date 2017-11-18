(ns dynadoc.core
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
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

(defn page [ns]
  (let [state (atom {:nses (->> (all-ns) (mapv ns-name) sort)
                     :vars (when ns (keys (ns-publics ns)))})]
    (-> "template.html" io/resource slurp
        (str/replace "{{content}}" (rum/render-html (common/app state)))
        (str/replace "{{initial-state}}" (pr-str @state)))))

(defn handler [request]
  (or (when (= (:uri request) "/")
        {:status 200
         :headers {"Content-Type" "text/html"}
         :body (page nil)})
      (let [ns (symbol (subs (:uri request) 1))]
        (when (contains? (->> (all-ns) (mapv ns-name) set) ns)
          {:status 200
           :headers {"Content-Type" "text/html"}
           :body (page ns)}))
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

