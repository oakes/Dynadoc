(ns dynadoc.core
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [ring.middleware.resource :refer [wrap-resource]]
            [ring.middleware.file :refer [wrap-file]]
            [ring.util.response :refer [redirect]]
            [ring.util.request :refer [body-string]]
            [org.httpkit.server :refer [run-server]]
            [rum.core :as rum]
            [dynadoc.common :as common]))

(defonce web-server (atom nil))
(defonce options (atom nil))
(defonce state (atom {:ns-names (sort (mapv ns-name (all-ns)))}))

(defn handler [request]
  (case (:uri request)
    "/" {:status 200
         :headers {"Content-Type" "text/html"}
         :body (-> "index.html" io/resource slurp
                   (str/replace "{{content}}" (rum/render-html (common/app state)))
                   (str/replace "{{initial-state}}" (pr-str @state)))}
    nil))

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
    (start (wrap-file handler "target/dynadoc-public") opts)))

