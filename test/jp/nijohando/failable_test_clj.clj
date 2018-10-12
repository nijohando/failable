(ns jp.nijohando.failable-test-clj
  (:refer-clojure :exclude [ensure])
  (:require [clojure.test :as t :refer [run-tests is deftest testing]]
            [jp.nijohando.failable :as f]))

(deftest ensure
  (testing "Exception must contain failure's cause"
    (let [rootex (ex-info "root" {})
          ex (try (f/ensure (f/do* (throw rootex))) (catch Exception e e))]
      (is (some? ex))
      (is (= rootex (.getCause ex)))))
  (testing "Exception must contain nested failure's cause"
    (let [rootex (ex-info "root" {})
          ex (try (f/ensure (-> (f/do* (throw rootex))
                                (f/wrap :foo)
                                (f/wrap :bar))) (catch Exception e e))]
      (is (some? ex))
      (is (= rootex (.getCause ex))))))
