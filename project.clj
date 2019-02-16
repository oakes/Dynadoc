(defproject dynadoc "1.5.5-SNAPSHOT"
  :description "A dynamic documentation generator"
  :url "https://github.com/oakes/Dynadoc"
  :license {:name "Public Domain"
            :url "http://unlicense.org/UNLICENSE"}
  :plugins [[lein-tools-deps "0.4.3"]]
  :middleware [lein-tools-deps.plugin/resolve-dependencies-with-deps-edn]
  :lein-tools-deps/config {:config-files [:install :user :project]}
  :profiles {:dev {:main dynadoc.core}})
