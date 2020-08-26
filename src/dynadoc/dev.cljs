(ns dynadoc.dev
  (:require [dynadoc.core :as c]
            [dynadoc.common :as common]
            [dynadoc.examples]
            [orchestra-cljs.spec.test :as st]
            [expound.alpha :as expound]
            [clojure.spec.alpha :as s]))

(st/instrument)
(set! s/*explain-out* expound/printer)

(->> {::common/prod? false}
     (common/update-session ::common/client)
     c/strip-nses
     (swap! c/*state merge))
