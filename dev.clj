(require
  '[orchestra.spec.test :as st]
  '[figwheel.main :as figwheel]
  '[dynadoc.core :as dynadoc])

(st/instrument)
(dynadoc/dev-start {:port 5000 :dev? true})
(figwheel/-main "--build" "dev")
