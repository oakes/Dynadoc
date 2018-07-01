(defn read-deps-edn [aliases-to-include]
  (let [{:keys [paths deps aliases]} (-> "deps.edn" slurp clojure.edn/read-string)
        deps (->> (select-keys aliases aliases-to-include)
                  vals
                  (mapcat :extra-deps)
                  (into deps)
                  (reduce
                    (fn [deps [artifact info]]
                      (if-let [version (:mvn/version info)]
                        (conj deps
                          (transduce cat conj [artifact version]
                            (select-keys info [:scope :exclusions])))
                        deps))
                    []))]
    {:dependencies deps
     :source-paths (set paths)
     :resource-paths (set paths)}))

(let [{:keys [source-paths resource-paths dependencies]} (read-deps-edn [])]
  (set-env!
    :source-paths source-paths
    :resource-paths resource-paths
    :dependencies (into '[[adzerk/boot-cljs "2.1.4" :scope "test"]
                          [adzerk/boot-reload "0.5.2" :scope "test"]
                          [org.clojure/test.check "0.9.0" :scope "test"]
                          [nightlight "RELEASE" :scope "test"]
                          [javax.xml.bind/jaxb-api "2.3.0" :scope "test"] ; necessary for Java 9 compatibility
                          [orchestra "2017.11.12-1" :scope "test"]]
                        dependencies)
    :repositories (conj (get-env :repositories)
                    ["clojars" {:url "https://clojars.org/repo/"
                                :username (System/getenv "CLOJARS_USER")
                                :password (System/getenv "CLOJARS_PASS")}])))

(require
  '[orchestra.spec.test :refer [instrument]]
  '[adzerk.boot-cljs :refer [cljs]]
  '[adzerk.boot-reload :refer [reload]]
  '[nightlight.boot :refer [nightlight]])

(task-options!
  pom {:project 'dynadoc
       :version "1.4.9"
       :description "A dynamic documentation generator"
       :url "https://github.com/oakes/Dynadoc"
       :license {"Public Domain" "http://unlicense.org/UNLICENSE"}}
  push {:repo "clojars"}
  sift {:include #{#"dynadoc-public/main.out"}
        :invert true})

(deftask local []
  (set-env! :resource-paths #(conj % "prod-resources"))
  (comp (cljs) (sift) (pom) (jar) (install)))

(deftask deploy []
  (set-env! :resource-paths #(conj % "prod-resources"))
  (comp (cljs) (sift) (pom) (jar) (push)))

(deftask run []
  (set-env! :resource-paths #(conj % "dev-resources"))
  (comp
    (watch)
    (nightlight :port 4000 :url "http://localhost:5000")
    (reload :asset-path "dynadoc-public")
    (cljs)
    (with-pass-thru _
      (require '[dynadoc.core :refer [dev-start]])
      (instrument)
      ((resolve 'dev-start) {:port 5000 :dev? true}))
    (target)))

