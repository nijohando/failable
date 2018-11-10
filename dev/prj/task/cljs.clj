(ns prj.task.cljs
  (:require [jp.nijohando.prj.core :refer [deftask]]
            [prj.cljs]))

(deftask test-cljs
  [_]
  (prj.cljs/test-cljs))

