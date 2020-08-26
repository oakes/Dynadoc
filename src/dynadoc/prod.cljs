(ns dynadoc.prod
  (:require [dynadoc.core :as c]
            [dynadoc.common :as common]))

(->> {::common/prod? true}
     (common/update-session ::common/client)
     c/strip-nses
     (swap! c/*state merge))
