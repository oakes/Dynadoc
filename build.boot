(set-env!
  :source-paths #{"src/clj" "src/cljc" "src/cljs"}
  :dependencies '[[adzerk/boot-cljs "2.1.4" :scope "test"]
                  [adzerk/boot-reload "0.5.2" :scope "test"]
                  [org.clojure/test.check "0.9.0" :scope "test"]
                  ; cljs deps
                  [org.clojure/clojurescript "1.9.946" :scope "test"]
                  [paren-soup "2.9.1" :scope "test"]
                  [mistakes-were-made "1.7.3" :scope "test"]
                  ; clj deps
                  [org.clojure/clojure "1.8.0" :scope "provided"]
                  [nightlight "1.9.3" :scope "test"]
                  [javax.xml.bind/jaxb-api "2.3.0"] ; necessary for Java 9 compatibility
                  [defexample "1.3.0"]
                  [ring "1.6.1"]
                  [http-kit "2.2.0"]
                  [eval-soup "1.2.2"]
                  [rum "0.10.8"]
                  [html-soup "1.5.0"]]
  :repositories (conj (get-env :repositories)
                  ["clojars" {:url "https://clojars.org/repo/"
                              :username (System/getenv "CLOJARS_USER")
                              :password (System/getenv "CLOJARS_PASS")}]))

(require
  '[adzerk.boot-cljs :refer [cljs]]
  '[adzerk.boot-reload :refer [reload]]
  '[dynadoc.core :refer [dev-start]]
  '[clojure.spec.test.alpha :refer [instrument]]
  '[nightlight.boot :refer [nightlight]])

(task-options!
  pom {:project 'dynadoc
       :version "1.0.0-SNAPSHOT"
       :description "A dynamic documentation generator"
       :url "https://github.com/oakes/Dynadoc"
       :license {"Public Domain" "http://unlicense.org/UNLICENSE"}}
  push {:repo "clojars"}
  sift {:include #{#"dynadoc-public/main.out"}
        :invert true})

(deftask local []
  (set-env! :resource-paths #{"src/clj" "src/cljc" "src/cljs" "resources" "prod-resources"})
  (comp (cljs) (sift) (pom) (jar) (install)))

(deftask deploy []
  (set-env! :resource-paths #{"src/clj" "src/cljc" "src/cljs" "resources" "prod-resources"})
  (comp (cljs) (sift) (pom) (jar) (push)))

(deftask run []
  (set-env! :resource-paths #{"src/clj" "src/cljc" "src/cljs" "resources" "dev-resources"})
  (comp
    (watch)
    (nightlight :port 4000 :url "http://localhost:5000")
    (reload :asset-path "dynadoc-public")
    (cljs)
    (with-pass-thru _
      (instrument)
      (dev-start {:port 5000 :url "http://localhost:5000"}))
    (target)))

