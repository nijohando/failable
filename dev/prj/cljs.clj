(ns prj.cljs
  (:require [jp.nijohando.prj.cljs :as prj-cljs]
            [jp.nijohando.prj.cljs.test :as prj-cljs-test]))

(defn test-cljs
  ([]
   (test-cljs "dev/dev.cljs.edn" "dev/test.cljs.edn"))
  ([& paths]
   (let [copts (apply prj-cljs/compiler-options paths)
         conf {:source-paths ["src" "test"]
               :compiler copts}]
     (prj-cljs-test/run-tests conf ['jp.nijohando.failable-test
                                    'jp.nijohando.failable-test-cljs]))))
