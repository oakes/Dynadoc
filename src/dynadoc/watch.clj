(ns dynadoc.watch
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [org.httpkit.server :refer [with-channel on-receive on-close]]
            [hawk.core :as hawk]
            [dynadoc.static :as static]
            [dynadoc.utils :as u]))

(defonce *channel->uri (atom {}))
(defonce *cljs-info (atom nil))

(defn init-watcher! []
  (reset! *cljs-info (static/get-cljs-nses-and-vars))
  (hawk/watch! [{:paths [(.getCanonicalPath (io/file "."))]
                 :handler (fn [ctx {:keys [kind file]}]
                            (when (#{:create :modify} kind)
                              (when (str/ends-with? (.getName file) ".cljs")
                                (try
                                  (swap! *cljs-info #(static/read-cljs-file % file))
                                  (catch Exception _))))
                            ctx)}]))

(defn watch-request [request]
  (with-channel request channel
    (on-close channel
      (fn [status]
        (swap! *channel->uri dissoc channel)))
    (on-receive channel
      (fn [uri]
        (swap! *channel->uri assoc channel uri)))))

