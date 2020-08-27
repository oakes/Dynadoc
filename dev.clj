(require
  '[orchestra.spec.test :as st]
  '[expound.alpha :as expound]
  '[clojure.spec.alpha :as s]
  '[figwheel.main :as figwheel]
  '[dynadoc.core :as dynadoc]
  '[dynadoc.examples])

(st/instrument)
(alter-var-root #'s/*explain-out* (constantly expound/printer))
(dynadoc/dev-start {:port 5000 :dev? true})
(figwheel/-main "--build" "dev")
