(ns dynadoc.watch
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [org.httpkit.server :refer [with-channel on-receive on-close]]
            [hawk.core :as hawk]
            [dynadoc.static :as static]
            [dynadoc.utils :as u]))

(defonce *channel->uri (atom {}))
(defonce *cljs-info (atom nil))

(def watcher
  {:paths [(.getCanonicalPath (io/file "."))]
   :handler (fn [ctx {:keys [kind file]}]
              (when (#{:create :modify} kind)
                (when (or (str/ends-with? (.getName file) ".cljs")
                          (str/ends-with? (.getName file) ".cljc"))
                  (try
                    (swap! *cljs-info #(static/read-cljs-file % file))
                    (catch Exception _))))
              ctx)})

(defn init-watcher! []
  (reset! *cljs-info (static/get-cljs-nses-and-vars))
  (try
    ;; if figwheel is on the classpath, use its file watching stuff
    ;; because otherwise it will kill the file watcher we start here
    (require 'figwheel.main.watching)
    ((resolve 'figwheel.main.watching/add-watch!) ::watcher watcher)
    (catch Exception _
      (hawk/watch! [watcher]))))

(defn watch-request [request]
  (with-channel request channel
    (on-close channel
      (fn [status]
        (swap! *channel->uri dissoc channel)))
    (on-receive channel
      (fn [uri]
        (swap! *channel->uri assoc channel uri)))))

