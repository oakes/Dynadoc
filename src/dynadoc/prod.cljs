(ns dynadoc.prod
  (:require [dynadoc.core :as c]
            [dynadoc.common :as common]))

(swap! common/*session common/update-session ::common/client {::common/prod? true})
