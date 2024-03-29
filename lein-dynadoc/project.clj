(defproject dynadoc/lein-dynadoc "1.7.5.1"
  :description "A conveninent Dynadoc launcher for Leiningen projects"
  :url "https://github.com/oakes/Dynadoc"
  :license {:name "Public Domain"
            :url "http://unlicense.org/UNLICENSE"}
  :dependencies [[dynadoc "1.7.5"]
                 [leinjacker "0.4.3"]
                 [org.clojure/tools.cli "1.0.214"]]
  :repositories [["clojars" {:url "https://clojars.org/repo"
                             :sign-releases false}]]
  :eval-in-leiningen true)

