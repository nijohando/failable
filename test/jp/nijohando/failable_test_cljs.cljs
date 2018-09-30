(ns jp.nijohando.failable-test-cljs
  (:require [clojure.test :as t :refer-macros [is deftest testing async]]
            [jp.nijohando.failable :as f :include-macros true]
            [clojure.core.async :as ca :include-macros true]))

(deftest slet-no-function-creation-boundary-test
  (testing "slet can be used in a go(...) block"
    (async done
      (ca/go
        (f/slet [ch (ca/chan 1)
                 _ (ca/>! ch :d1)
                 r (ca/<! ch)]
          (is (= r :d1))
          (done))))))

(deftest slet*-no-function-creation-boundary-test
  (testing "slet* can be used in a go(...) block"
    (async done
      (ca/go
        (f/slet* [ch (ca/chan 1)
                  _ (ca/>! ch :d1)
                  r (ca/<! ch)]
          (is (= r :d1))
          (done))))))

(deftest flet-no-function-creation-boundary-test
  (testing "flet can be used in a go(...) block"
    (async done
      (ca/go
        (let [ch (ca/chan 1)]
          (ca/>! ch (f/fail :test))
          (f/flet [f (ca/<! ch)]
            (is (f/fail? f))
            (is (= :test @f))
            (done)))))))

(deftest flet*-no-function-creation-boundary-test
  (testing "flet* can be used in a go(...) block"
    (async done
      (ca/go
        (let [ch (ca/chan 1)]
          (ca/>! ch (f/fail :test))
          (f/flet* [f (ca/<! ch)]
            (is (f/fail? f))
            (is (= :test @f))
            (done)))))))

(deftest if-succ-no-function-creation-boundary-test
  (testing "if-succ can be use d in a go(...) block"
    (async done
      (ca/go
        (let [ch (ca/chan 1)
              _ (ca/>! ch 10)]
          (is (= 10 (f/if-succ [x (ca/<! ch)]
                      x))))
        (done)))))

(deftest if-succ*-no-function-creation-boundary-test
  (testing "if-succ* can be use d in a go(...) block"
    (async done
      (ca/go
        (let [ch (ca/chan 1)
              _ (ca/>! ch 10)]
          (is (= 10 (f/if-succ* [x (ca/<! ch)]
                      x))))
        (done)))))

(deftest if-fail-no-function-creation-boundary-test
  (testing "if-fail can be use d in a go(...) block"
    (async done
      (ca/go
        (let [ch (ca/chan 1)
              _ (ca/>! ch (f/fail :foo-error))]
          (is (= (f/if-fail [x (ca/<! ch)]
                   @x)
                 :foo-error)))
        (done)))))

(deftest if-fail*-no-function-creation-boundary-test
  (testing "if-fail* can be use d in a go(...) block"
    (async done
      (ca/go
        (let [ch (ca/chan 1)
              _ (ca/>! ch (f/fail :foo-error))]
          (is (= (f/if-fail* [x (ca/<! ch)]
                   @x)
                 :foo-error)))
        (done)))))

(deftest when-succ-no-function-creation-boundary-test
  (testing "when-succ can be use d in a go(...) block"
    (async done
      (ca/go
        (let [ch (ca/chan 1)
              _ (ca/>! ch 10)]
          (is (= 10 (f/when-succ [x (ca/<! ch)]
                      x))))
        (done)))))

(deftest when-succ*-no-function-creation-boundary-test
  (testing "when-succ* can be use d in a go(...) block"
    (async done
      (ca/go
        (let [ch (ca/chan 1)
              _ (ca/>! ch 10)]
          (is (= 10 (f/when-succ* [x (ca/<! ch)]
                      x))))
        (done)))))

(deftest when-fail-no-function-creation-boundary-test
  (testing "when-fail can be use d in a go(...) block"
    (async done
      (ca/go
        (let [ch (ca/chan 1)
              _ (ca/>! ch (f/fail :foo-error))]
          (is (= (f/when-fail [x (ca/<! ch)]
                   @x)
                 :foo-error)))
        (done)))))

(deftest when-fail*-no-function-creation-boundary-test
  (testing "when-fail* can be use d in a go(...) block"
    (async done
      (ca/go
        (let [ch (ca/chan 1)
              _ (ca/>! ch (f/fail :foo-error))]
          (is (= (f/when-fail* [x (ca/<! ch)]
                   @x)
                 :foo-error)))
        (done)))))

(deftest succ->-no-function-creation-boundary-test
  (testing "succ-> can be use d in a go(...) block"
    (async done
      (ca/go
        (let [ch (ca/chan 1)
              _ (ca/>! ch 10)]
          (is (= 8 (f/succ-> (ca/<! ch)
                             (dec)
                             (dec)))))
        (done)))))

(deftest succ->*-no-function-creation-boundary-test
  (testing "succ->* can be use d in a go(...) block"
    (async done
      (ca/go
        (let [ch (ca/chan 1)
              _ (ca/>! ch 10)]
          (is (= 8 (f/succ->* (ca/<! ch)
                              (dec)
                              (dec)))))
        (done)))))

(deftest succ->>-no-function-creation-boundary-test
  (testing "succ->> can be use d in a go(...) block"
    (async done
      (ca/go
        (let [ch (ca/chan 1)
              _ (ca/>! ch 10)]
          (is (= 8 (f/succ->> (ca/<! ch)
                              (dec)
                              (dec)))))
        (done)))))

(deftest succ->>*-no-function-creation-boundary-test
  (testing "succ->>* can be use d in a go(...) block"
    (async done
      (ca/go
        (let [ch (ca/chan 1)
              _ (ca/>! ch 10)]
          (is (= 8 (f/succ->>* (ca/<! ch)
                               (dec)
                               (dec)))))
        (done)))))

(deftest fail->-no-function-creation-boundary-test
  (testing "fail-> can be use d in a go(...) block"
    (async done
      (ca/go
        (let [ch (ca/chan 1)
              _ (ca/>! ch (f/fail :test))]
          (is (= :test @(f/fail-> (ca/<! ch)))))
        (done)))))

(deftest fail->>-no-function-creation-boundary-test
  (testing "fail->> can be use d in a go(...) block"
    (async done
      (ca/go
        (let [ch (ca/chan 1)
              _ (ca/>! ch (f/fail :test))]
          (is (= :test @(f/fail->> (ca/<! ch)))))
        (done)))))

(deftest as-succ->-no-function-creation-boundary-test
  (testing "as-succ-> can be used in a go(...) block"
    (async done
      (ca/go
        (let [ch (ca/chan 1)
              _ (ca/>! ch 10)]
          (is (= 11 (f/as-succ-> (ca/<! ch) n
                      (inc n))))
          (done))))))

(deftest as-succ->*-no-function-creation-boundary-test
  (testing "as-succ->* can be used in a go(...) block"
    (async done
      (ca/go
        (let [ch (ca/chan 1)
              _ (ca/>! ch 10)]
          (is (= 11 (f/as-succ->* (ca/<! ch) n
                      (inc n))))
          (done))))))

(deftest as-fail->-no-function-creation-boundary-test
  (testing "as-fail-> can be used in a go(...) block"
    (async done
      (ca/go
        (let [ch (ca/chan 1)
              _ (ca/>! ch (f/fail :test))]
          (is (= :test @(f/as-fail-> (ca/<! ch) n)))
          (done))))))

(deftest as-fail->*-no-function-creation-boundary-test
  (testing "as-fail->* can be used in a go(...) block"
    (async done
      (ca/go
        (let [ch (ca/chan 1)
              _ (ca/>! ch (f/fail :test))]
          (is (= :test @(f/as-fail->* (ca/<! ch) n)))
          (done))))))
