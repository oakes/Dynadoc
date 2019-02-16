(ns leiningen.dynadoc
  (:require [leinjacker.deps :as deps]
            [leinjacker.eval :as eval]
            [clojure.tools.cli :as cli]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [dynadoc.utils :as u]))


(defn start-dynadoc
  [{:keys [main] :as project} options]
  (eval/eval-in-project
    (deps/add-if-missing
      project
      '[dynadoc/lein-dynadoc "1.5.6"])
    `(do
       (dynadoc.core/start ~options)
       (when '~main (require '~main)))
    `(require 'dynadoc.core)))


(defn dynadoc
  "A conveninent Dynadoc launcher
  Run with -u to see CLI usage."
  [project & args]
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
      (start-dynadoc project (:options cli)))))

