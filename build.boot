(set-env!
  :source-paths #{"src/clj" "src/cljc" "src/cljs"}
  :dependencies '[[adzerk/boot-cljs "2.1.4" :scope "test"]
                  [adzerk/boot-reload "0.5.2" :scope "test"]
                  [org.clojure/test.check "0.9.0" :scope "test"]
                  [nightlight "2.1.0" :scope "test"]
                  [org.clojars.oakes/boot-tools-deps "0.1.4.1" :scope "test"]]
  :repositories (conj (get-env :repositories)
                  ["clojars" {:url "https://clojars.org/repo/"
                              :username (System/getenv "CLOJARS_USER")
                              :password (System/getenv "CLOJARS_PASS")}]))

(require
  '[adzerk.boot-cljs :refer [cljs]]
  '[adzerk.boot-reload :refer [reload]]
  '[nightlight.boot :refer [nightlight]]
  '[boot-tools-deps.core :refer [deps]])

(task-options!
  pom {:project 'dynadoc
       :version "1.2.1-SNAPSHOT"
       :description "A dynamic documentation generator"
       :url "https://github.com/oakes/Dynadoc"
       :license {"Public Domain" "http://unlicense.org/UNLICENSE"}}
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
    (reload :on-jsload 'dynadoc.core/reload :asset-path "dynadoc-public")
    (cljs)
    (with-pass-thru _
      (require
        '[dynadoc.core :refer [dev-start]]
        '[clojure.spec.test.alpha :refer [instrument]])
      ((resolve 'instrument))
      ((resolve 'dev-start) {:port 5000}))
    (target)))

