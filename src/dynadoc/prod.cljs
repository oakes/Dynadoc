(ns dynadoc.prod
  (:require [dynadoc.core :as c]
            [dynadoc.common :as common]))

(->> {::common/prod? true}
     (common/update-session! ::common/client))
