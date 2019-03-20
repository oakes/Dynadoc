(defproject dynadoc "1.6.5"
  :description "A dynamic documentation generator"
  :url "https://github.com/oakes/Dynadoc"
  :license {:name "Public Domain"
            :url "http://unlicense.org/UNLICENSE"}
  :repositories [["clojars" {:url "https://clojars.org/repo"
                             :sign-releases false}]]
  :profiles {:dev {:main dynadoc.core}})
