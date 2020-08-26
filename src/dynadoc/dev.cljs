(ns dynadoc.dev
  (:require [dynadoc.core :as c]
            [dynadoc.examples]
            [orchestra-cljs.spec.test :as st]
            [expound.alpha :as expound]
            [clojure.spec.alpha :as s]))

(st/instrument)
(set! s/*explain-out* expound/printer)
