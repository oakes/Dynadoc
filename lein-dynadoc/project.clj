(defproject dynadoc/lein-dynadoc "1.6.5"
  :description "A conveninent Dynadoc launcher for Leiningen projects"
  :url "https://github.com/oakes/Dynadoc"
  :license {:name "Public Domain"
            :url "http://unlicense.org/UNLICENSE"}
  :dependencies [[dynadoc "1.6.5"]
                 [leinjacker "0.4.2"]
                 [org.clojure/tools.cli "0.3.5"]]
  :repositories [["clojars" {:url "https://clojars.org/repo"
                             :sign-releases false}]]
  :eval-in-leiningen true)

