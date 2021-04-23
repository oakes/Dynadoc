(require
  '[clojure.spec.test.alpha :as st]
  '[clojure.spec.alpha :as s]
  '[figwheel.main :as figwheel]
  '[dynadoc.core :as dynadoc]
  '[dynadoc.examples])

(st/instrument)
(st/unstrument 'odoyle.rules/insert) ;; don't require specs for attributes
(dynadoc/dev-start {:port 5000 :dev? true})
(figwheel/-main "--build" "dev")
