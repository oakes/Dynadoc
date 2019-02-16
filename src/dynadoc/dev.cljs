(ns dynadoc.dev
  (:require [dynadoc.core :as c]
            [dynadoc.examples]
            [orchestra-cljs.spec.test :as st]))

(st/instrument)
(c/init)
