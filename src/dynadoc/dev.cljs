(ns dynadoc.dev
  (:require [dynadoc.core :as c]
            [dynadoc.examples]
            [clojure.spec.test.alpha :as st]
            [clojure.spec.alpha :as s]))

(st/instrument)
(st/unstrument 'odoyle.rules/insert) ;; don't require specs for attributes
