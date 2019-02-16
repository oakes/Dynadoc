(require
  '[orchestra.spec.test :refer [instrument]]
  '[figwheel.main :as figwheel]
  '[dynadoc.core :as dynadoc])

(dynadoc/dev-start {:port 5000 :dev? true})
(figwheel/-main "--build" "dev")
