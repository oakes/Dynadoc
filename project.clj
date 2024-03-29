(defproject dynadoc "1.7.6-SNAPSHOT"
  :description "A dynamic documentation generator"
  :url "https://github.com/oakes/Dynadoc"
  :license {:name "Public Domain"
            :url "http://unlicense.org/UNLICENSE"}
  :repositories [["clojars" {:url "https://clojars.org/repo"
                             :sign-releases false}]]
  :profiles {:dev {:main dynadoc.core}})
