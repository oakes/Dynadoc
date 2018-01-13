(set-env!
  :source-paths #{"src/clj" "src/cljc" "src/cljs"}
  :dependencies '[[adzerk/boot-cljs "2.1.4" :scope "test"]
                  [adzerk/boot-reload "0.5.2" :scope "test"]
                  [org.clojure/test.check "0.9.0" :scope "test"]
                  [nightlight "RELEASE" :scope "test"]
                  [seancorfield/boot-tools-deps "0.1.4" :scope "test"]
                  [javax.xml.bind/jaxb-api "2.3.0"] ; necessary for Java 9 compatibility
                  [orchestra "2017.11.12-1" :scope "test"]]
  :repositories (conj (get-env :repositories)
                  ["clojars" {:url "https://clojars.org/repo/"
                              :username (System/getenv "CLOJARS_USER")
                              :password (System/getenv "CLOJARS_PASS")}]))

(require
  '[orchestra.spec.test :refer [instrument]]
  '[clojure.edn :as edn]
  '[adzerk.boot-cljs :refer [cljs]]
  '[adzerk.boot-reload :refer [reload]]
  '[nightlight.boot :refer [nightlight]]
  '[boot-tools-deps.core :refer [deps]])

(task-options!
  pom {:project 'dynadoc
       :version "1.4.4-SNAPSHOT"
       :description "A dynamic documentation generator"
       :url "https://github.com/oakes/Dynadoc"
       :license {"Public Domain" "http://unlicense.org/UNLICENSE"}
       :dependencies (->> "deps.edn"
                          slurp
                          edn/read-string
                          :deps
                          (reduce
                            (fn [deps [artifact info]]
                              (if-let [version (:mvn/version info)]
                                (conj deps
                                  (transduce cat conj [artifact version]
                                    (select-keys info [:scope :exclusions])))
                                deps))
                            []))}
  push {:repo "clojars"}
  sift {:include #{#"dynadoc-public/main.out"}
        :invert true})

(deftask local []
  (set-env! :resource-paths #{"src/clj" "src/cljc" "src/cljs" "resources" "prod-resources"})
  (comp (deps) (cljs) (sift) (pom) (jar) (install)))

(deftask deploy []
  (set-env! :resource-paths #{"src/clj" "src/cljc" "src/cljs" "resources" "prod-resources"})
  (comp (deps) (cljs) (sift) (pom) (jar) (push)))

(deftask run []
  (set-env! :resource-paths #{"src/clj" "src/cljc" "src/cljs" "resources" "dev-resources"})
  (comp
    (deps)
    (watch)
    (nightlight :port 4000 :url "http://localhost:5000")
    (reload :asset-path "dynadoc-public")
    (cljs)
    (with-pass-thru _
      (require '[dynadoc.core :refer [dev-start]])
      ((resolve 'dev-start) {:port 5000 :dev? true}))
    (target)))

