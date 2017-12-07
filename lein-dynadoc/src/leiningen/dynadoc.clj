(ns leiningen.dynadoc
  (:require [leinjacker.deps :as deps]
            [leinjacker.eval :as eval]
            [clojure.tools.cli :as cli]
            [clojure.edn :as edn]
            [clojure.string :as str]))

(def cli-options
  [["-p" "--port PORT" "Port number"
    :default 5000
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 % 0x10000) "Must be an integer between 0 and 65536"]]
   [nil "--host HOST" "The hostname that Dynadoc listens on"
    :default "0.0.0.0"]
   ["-u" "--usage" "Show CLI usage options"]])


(defn start-dynadoc
  [{:keys [main] :as project} {:keys [port host] :as options}]
  (eval/eval-in-project
    (deps/add-if-missing
      project
      '[dynadoc/lein-dynadoc "1.1.4"])
    `(do
       (dynadoc.core/start
         {:port ~port :ip ~host})
       (when '~main (require '~main)))
    `(require 'dynadoc.core)))


(defn dynadoc
  "A conveninent Dynadoc launcher
  Run with -u to see CLI usage."
  [project & args]
  (let [cli (cli/parse-opts args cli-options)]
    (cond
      ;; if there are CLI errors, print error messages and usage summary
      (:errors cli)
      (println (:errors cli) "\n" (:summary cli))
      ;; if user asked for CLI usage, print the usage summary
      (get-in cli [:options :usage])
      (println (:summary cli))
      ;; in other cases start Nightlight
      :otherwise
      (start-dynadoc project (:options cli)))))

