(ns dynadoc.boot
  {:boot/export-tasks true}
  (:require [dynadoc.core :refer [start]]
            [boot.core :as core]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(core/deftask dynadoc
  [p port PORT int "The port that Dynadoc runs on"
   _ host HOST str "The hostname that Dynadoc listens on"]
  (core/with-pass-thru _
    (start {:port (or port 4000)
            :ip (or host "0.0.0.0")})))

