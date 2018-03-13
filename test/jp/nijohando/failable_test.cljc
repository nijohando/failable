(ns jp.nijohando.failable-test
  (:require #?(:clj  [clojure.test :as t :refer [run-tests is are deftest testing]]
               :cljs [cljs.test :as t :refer-macros [run-tests is are deftest testing]])
            [jp.nijohando.failable :as f :include-macros true])
  #?(:clj (:import [clojure.lang ExceptionInfo])))

(deftest throwable?-test
  (testing "Exception or js/Error must be true"
    (is (f/throwable? #? (:clj (Exception.) :cljs (js/Error.))))
    (is (f/throwable? #? (:clj (Error.) :cljs (js/Error.)))))
  (testing "Others are not be true"
    (let [not-throwable? (comp not f/throwable?)]
      (is (not-throwable? nil))
      (is (not-throwable? true))
      (is (not-throwable? false))
      (is (not-throwable? "foo")))))

(deftest fail?-test
  (testing "Failure must be true"
    (is (f/fail? (f/fail ::foo-error))))
  (testing "Others must not be false"
    (let [not-fail? (comp not f/fail?)]
      (is (not-fail? nil))
      (is (not-fail? true))
      (is (not-fail? false))
      (is (not-fail? "foo")))))

(deftest succ?-test
  (testing "Failure must be false"
    (is (not (f/succ? (f/fail ::foo-error)))))
  (testing "Others must be true"
    (is (f/succ? nil))
    (is (f/succ? true))
    (is (f/succ? false))
    (is (f/succ? "foo"))))

(deftest fail-test
  (testing "Failure can be created from reason"
    (let [fa (f/fail ::foo-error)
          th (f/throwable fa)
          attrs (f/attrs fa)]
      (is (f/fail? fa))
      (is (= @fa ::foo-error))
      (is (f/throwable? th))
      (is (= attrs {:reason ::foo-error}))))
  (testing "Failure can be created from reason and attributes"
    (let [fa (f/fail ::foo-error {:arg1 [:numeric]
                                  :arg2 [:max-length :min-length]})
          th (f/throwable fa)
          attrs (f/attrs fa)]
      (is (f/fail? fa))
      (is (= @fa ::foo-error))
      (is (f/throwable? th))
      (is (map? attrs))
      (is (= attrs {:reason ::foo-error
                    :arg1 [:numeric]
                    :arg2 [:max-length :min-length]}))))
  (testing "Failure can be created from reason and exception"
    (let [fa (f/fail ::foo-error (ex-info "foo-exception" {}) )
          th (f/throwable fa)
          attrs (f/attrs fa)]
      (is (f/fail? fa))
      (is (= @fa ::foo-error))
      (is (f/throwable? th))
      (is (= attrs {:reason ::foo-error}))))
  (testing "Failure can be created from reason and other failure"
    (let [fa (f/fail ::foo-error (f/fail :other))
          th (f/throwable fa)
          attrs (f/attrs fa)]
      (is (f/fail? fa))
      (is (= @fa ::foo-error))
      (is (f/throwable? th))
      (is (= attrs {:reason ::foo-error}))))
  (testing "Failure can be created from reason, attributes and exception"
    (let [cause (ex-info "other-exception" {})
          fa (f/fail ::foo-error {:foo :bar} cause)
          th (f/throwable fa)
          attrs (f/attrs fa)]
      (is (f/fail? fa))
      (is (= @fa ::foo-error))
      (is (f/throwable? th))
      (is (= attrs {:reason ::foo-error
                    :foo :bar}))))
  (testing "Failure can be created from reason, attributes and other failure"
    (let [cause (f/fail :other-failure)
          fa (f/fail ::foo-error {:foo :bar} cause)
          th (f/throwable fa)
          attrs (f/attrs fa)]
      (is (f/fail? fa))
      (is (= @fa ::foo-error))
      (is (f/throwable? th))
      (is (= attrs {:reason ::foo-error
                    :foo :bar})))))

(deftest fthrow-test
  (testing "Failure can be thrown"
    (is (thrown-with-msg? #?(:clj Exception :cljs js/Error) #"^:foo-error$" (f/fthrow (f/fail :foo-error)))))
  (testing "Throwable can be thrown"
    (is (thrown-with-msg? #?(:clj Exception :cljs js/Error) #"^foo-error$" (f/fthrow (ex-info "foo-error" {}))))))

(deftest fensure-test
  (testing "Value is just returned if it's not failure"
    (is (= :ok (f/fensure :ok)))
    (is (= true (f/fensure true)))
    (is (= false (f/fensure false)))
    (is (nil? (f/fensure nil))))
  (testing "Exception is thrown if value is failure"
    (let [e (ex-info "error1" {})
          x (f/fail :foo-error e)]
      (is (thrown-with-msg? #?(:clj Exception :cljs js/Error) #"^:jp.nijohando.failable/failed" (f/fensure x))))))

(deftest fdo-test
  (testing "Forms are evaluated and the last value is returned"
    (let [x (atom [])]
      (is (= (f/fdo
              (swap! x conj :d1)
              (swap! x conj :d2)) [:d1 :d2]))))
  (testing "Exception is captured and returned"
    (let [x (f/fdo (throw #?(:clj (Exception.) :cljs (js/Error.))))]
      (is (f/fail? x)))))

(deftest flet-return-value-test
  (testing "Body value is returned"
    (is (= 1 (f/flet [_ nil] 1))))
  (testing "Body failure value is returned"
    (let [x (f/fail ::foo-error)]
      (is (= x (f/flet [_ nil]
                       x)))))
  (testing "Binding form is returned"
    (is (= 1 (f/flet [a 1] a))))
  (testing "Body form is evaluated and returned"
    (is (= 2 (f/flet [a 1] (inc a)))))
  (testing "Failure on bindings is just returned"
    (let [x (f/fail "error1")
          y (f/flet [_ x] 1)]
      (is (f/fail? y))
      (is (= x y))))
  (testing "Exception on bindings is not captured"
    (is (thrown-with-msg?
         #?(:clj Exception :cljs js/Error) #"^:foo-error$"
         (f/flet [_ (f/fthrow (f/fail :foo-error))]
           nil))))
  (testing "Exception on body is not captured"
    (is (thrown-with-msg?
         #?(:clj Exception :cljs js/Error) #"^:foo-error$"
         (f/flet [_ 1]
           (f/fthrow (f/fail :foo-error)))))))

(deftest flet-discontinuation
  (testing "Binding evaluation is stopped by failure"
    (let [mark (atom [])
          x (f/flet [a (do (swap! mark conj :step1)
                           1)
                     b (do (swap! mark conj :step2)
                           (f/fail ::foo-error))
                     c (do (swap! mark conj :step3)
                           3)]
              (+ a b))]
      (is (f/fail? x))
      (is (= [:step1 :step2] @mark))
      (is (= @x ::foo-error)))))

(deftest flet-destructing-test
  (testing "Binding form is returned"
    (is (= 1 (f/flet [{:keys [a]} {:a 1}]
               a))))
  (testing "Binding evaluation is stopped by failure"
    (let [mark (atom [])
          x (f/flet [{:keys [a]} (do (swap! mark conj :step1)
                                     {:a 1})
                     {:keys [b]} (do (swap! mark conj :step2)
                                     (f/fail ::foo-error))
                     {:keys [c]} (do (swap! mark conj :step3)
                                     {:c 3})]
              (+ a b))]
      (is (f/fail? x))
      (is (= @mark [:step1 :step2]))
      (is (= @x ::foo-error)))))

(deftest flet*-return-value-test
  (testing "Body value is returned"
    (is (= 1 (f/flet* [_ nil] 1))))
  (testing "Body failure value is returned"
    (let [x (f/fail ::foo-error)]
      (is (= x (f/flet* [_ nil]
                       x)))))
  (testing "Binding form is returned"
    (is (= 1 (f/flet* [a 1] a))))
  (testing "Body form is evaluated and returned"
    (is (= 2 (f/flet* [a 1] (inc a)))))
  (testing "Failure is just returned"
    (let [x (f/fail ::foo-error)
          y (f/flet* [_ x] 1)]
      (is (f/fail? y))
      (is (= x y))))
  (testing "Exception on bindings is captured and returned as failure"
    (let [x (f/flet* [_ (throw (ex-info "error1" {}))]
              nil)]
      (is (f/fail? x))
      (is (= @x :jp.nijohando.failable/exceptionproof))))
  (testing "Exception on body is captured and returned as failure"
    (let [x (f/flet* [_ 1] (throw (ex-info "error1" {})))]
      (is (f/fail? x))
      (is (= @x :jp.nijohando.failable/exceptionproof)))))

(deftest flet*-discontinuation
  (testing "Binding evaluation is stopped by failure"
    (let [mark (atom [])
          x (f/flet* [a (do (swap! mark conj :step1)
                            1)
                      b (do (swap! mark conj :step2)
                            (f/fail ::foo-error))
                      c (do (swap! mark conj :step3)
                            3)]
              (+ a b))]
      (is (f/fail? x))
      (is (= @mark [:step1 :step2]))
      (is (= @x ::foo-error)))))

(deftest flet*-destructing-test
  (testing "Binding form is returned"
    (is (= 1 (f/flet* [{:keys [a]} {:a 1}]
               a))))
  (testing "Binding evaluation is stopped by failure"
    (let [mark (atom [])
          x (f/flet* [{:keys [a]} (do (swap! mark conj :step1)
                                      {:a 1})
                      {:keys [b]} (do (swap! mark conj :step2)
                                      (f/fail ::foo-error))
                      {:keys [c]} (do (swap! mark conj :step3)
                                      {:c 3})]
              (+ a b))]
      (is (f/fail? x))
      (is (= @mark [:step1 :step2]))
      (is (= @x ::foo-error)))))

(deftest succ->return-value-test
  (testing "Body forms are evaludated returned"
    (is (= 2 (f/succ-> 1
                    (inc)
                    (inc)
                    (dec)))))
  (testing "Failure is returned"
    (let [x (f/succ-> 1
                   ((fn [x] (f/fail ::foo-error))))]
      (is (f/fail? x))
      (is (= @x ::foo-error)))))

(deftest succ->discontinuation-test
  (testing "Threading evaluation is stopped by failure"
    (let [x (atom [])
          y (f/succ-> 1
                   ((fn [n]
                      (swap! x conj :a)
                      (inc n)))
                   ((fn [n]
                      (swap! x conj :b)
                      (inc n)))
                   ((fn [n]
                      (f/fail ::foo-error)))
                   ((fn [n]
                      (swap! x conj :c)
                      (inc n)))
                   (inc))]
      (is (f/fail? y))
      (is (= @y ::foo-error))
      (is (= @x [:a :b]))))
  (testing "Threading evaluation is interrupted by exception"
    (is (thrown-with-msg?
         #?(:clj Exception :cljs js/Error) #"^foo-exception$"
         (f/succ-> 1
                (inc)
                ((fn [x] (throw (ex-info "foo-exception" {})))))))))

(deftest succ->*return-value-test
  (testing "Body forms are evaludated returned"
    (is (= 2 (f/succ->* 1
                     (inc)
                     (inc)
                     (dec)))))
  (testing "Failure is returned"
    (let [x (f/succ->* 1
                    ((fn [x] (f/fail ::foo-error))))]
      (is (f/fail? x))
      (is (= @x ::foo-error))))

  (testing "Exception is captured and returned as failure"
    (let [x (f/succ->* 1
                    ((fn [x] (f/fail ::foo-error))))]
      (is (f/fail? x))
      (is @x :jp.nijohando.failable/exceptionproof))))

(deftest succ->*discontinuation-test
  (testing "Threading evaluation is stopped by failure"
    (let [x (atom [])
          y (f/succ->* 1
                    ((fn [n]
                       (swap! x conj :a)
                       (inc n)))
                    ((fn [n]
                       (swap! x conj :b)
                       (inc n)))
                    ((fn [n]
                       (f/fail ::foo-error)))
                    ((fn [n]
                       (swap! x conj :c)
                       (inc n)))
                    (inc))]
      (is (f/fail? y))
      (is (= @x [:a :b]))
      (is (= @y ::foo-error))))
  (testing "Threading evaluation is stopped by exception"
    (let [x (atom [])
          y (f/succ->* 1
                    ((fn [n]
                       (swap! x conj :a)
                       (inc n)))
                    ((fn [n]
                       (swap! x conj :b)
                       (inc n)))
                    ((fn [n]
                       (throw (ex-info "foo-exception" {}))))
                    ((fn [n]
                       (swap! x conj :c)
                       (inc n)))
                    (inc))]
      (is (f/fail? y))
      (is (= @x [:a :b]))
      (is (= @y :jp.nijohando.failable/exceptionproof)))))

(deftest succ->>return-value-test
  (testing "Body forms are evaludated returned"
    (is (= 2 (f/succ->> 1
                     (inc)
                     (inc)
                     (dec)))))
  (testing "Failure is returned"
    (let [x (f/succ->> 1
                    ((fn [x] (f/fail ::foo-error))))]
      (is (f/fail? x))
      (is (= @x ::foo-error)))))

(deftest succ->>discontinuation-test
  (testing "Threading evaluation is stopped by failure"
    (let [x (atom [])
          y (f/succ->> 1
                    ((fn [n]
                       (swap! x conj :a)
                       (inc n)))
                    ((fn [n]
                       (swap! x conj :b)
                       (inc n)))
                    ((fn [n]
                       (f/fail ::foo-error)))
                    ((fn [n]
                       (swap! x conj :c)
                       (inc n)))
                    (inc))]
      (is (f/fail? y))
      (is (= @x [:a :b]))
      (is (= @y ::foo-error))))
  (testing "Threading evaluation is interrupted by exception"
    (is (thrown-with-msg?
         #?(:clj Exception :cljs js/Error) #"^foo-exception$"
         (f/succ->> 1
                 (inc)
                 ((fn [x] (throw (ex-info "foo-exception" {})))))))))

(deftest succ->>*return-value-test
  (testing "Body forms are evaludated returned"
    (is (= -2 (f/succ->>* 1
                       (+ 1)
                       (+ 1)
                       (- 1)))))
  (testing "Failure is returned"
    (let [x (f/succ->>* 1
                     ((fn [x] (f/fail ::foo-error))))]
      (is (f/fail? x))
      (is (= @x ::foo-error))))
  (testing "Exception is captured and returned as failure"
    (let [x (f/succ->>* 1
                    ((fn [x] (f/fail ::foo-error))))]
      (is (f/fail? x))
      (is @x :jp.nijohando.failable/exceptionproof))))

(deftest if-succ-test
  (testing "Evaluates 'then' form if test is successful"
    (is (true? (f/if-succ [x 1]
                 (number? x)
                 false)))
    (is (true? (f/if-succ [x nil]
                 (nil? x)
                 false)))
    (is (true? (f/if-succ [x false]
                 (not x)
                 false))))
  (testing "Evaluates 'else' form if test is failed"
    (is (= 3 (f/if-succ [x (f/fail ::foo-error)]
               (inc 1)
               (inc 2)))))
  (testing "Returns nil if test is failed and 'else' form is not defined"
    (is (nil? (f/if-succ [x (f/fail ::foo-error)]
                false))))
  (testing "Exception on bindings is not captured and thrown"
    (is (thrown-with-msg?
         #?(:clj Exception :cljs js/Error) #"^foo-exception$"
         (f/if-succ [x (throw (ex-info "foo-exception" {}))]
           false))))
  (testing "Exception on body is not captured and thrown"
    (is (thrown-with-msg?
         #?(:clj Exception :cljs js/Error) #"^foo-exception$"
         (f/if-succ [x 1]
           (throw (ex-info "foo-exception" {}))
           false)))))

(deftest if-fail-test
  (testing "Evaluates 'then' form if test is failed"
    (is (= (f/if-fail [x (f/fail ::foo-error)]
             @x)
           ::foo-error)))
  (testing "Returns nil if test is successful and 'else' form is not defined"
    (is (nil? (f/if-fail [x 1]
                false))))
  (testing "Exception on bindings is not captured and thrown"
    (is (thrown-with-msg?
         #?(:clj Exception :cljs js/Error) #"^foo-exception$"
         (f/if-fail [x (throw (ex-info "foo-exception" {}))]
           false))))
  (testing "Exception on body is not captured and thrown"
    (is (thrown-with-msg?
         #?(:clj Exception :cljs js/Error) #"^foo-exception$"
         (f/if-fail [x (f/fail ::foo-error)]
           (throw (ex-info "foo-exception" {}))
           false)))))

(deftest if-succ*-test
  (testing "Evaluates 'then' form if test is successful"
    (is (true? (f/if-succ* [x 1]
                 (number? x)
                 false)))
    (is (true? (f/if-succ* [x nil]
                 (nil? x)
                 false)))
    (is (true? (f/if-succ* [x false]
                 (not x)
                 false))))
  (testing "Evaluates 'else' form if test is failed"
    (is (= 3 (f/if-succ* [x (f/fail ::foo-error)]
               (inc 1)
               (inc 2)))))
  (testing "Evaluates 'else' form if test throws exception"
    (is (true? (f/if-succ* [x (throw (ex-info "foo-exception" {}))]
                 false
                 true))))
  (testing "Returns nil if test is failed and 'else' form is not defined"
    (is (nil? (f/if-succ* [x (f/fail ::foo-error)]
                false))))
  (testing "Exception on body is captured and converted into failure"
    (let [x (f/if-succ* [x 1]
              (throw (ex-info "foo-exception" {}))
              false)]
      (is (f/fail? x))
      (is (= @x :jp.nijohando.failable/exceptionproof)))))

(deftest if-fail*-test
  (testing "Evaluates 'then' form if test is failed"
    (is (true? (f/if-fail* [x 1]
                 false
                 true)))
    (is (true? (f/if-fail* [x nil]
                 false
                 true)))
    (is (true? (f/if-fail* [x false]
                 false
                 true))))
  (testing "Evaluates 'else' form if test is successful"
    (is (true? (f/if-fail* [x 1]
                 false
                 true))))
  (testing "Evaluates 'else' form if test throws exception"
    (is (true? (f/if-fail* [x (throw (ex-info "foo-exception" {}))]
                 true
                 false))))
  (testing "Returns nil if test is successful and 'else' form is not defined"
    (is (nil? (f/if-fail* [x 1]
                false)))))

(deftest when-succ-test
  (testing "Evaluates 'body' form if test is successful"
    (is (true? (f/when-succ [x 1]
                 (number? x)))))
  (testing "Returns nil if test is failed"
    (is (nil? (f/when-succ [x (f/fail ::foo-error)]
                false))))
  (testing "Exception on bindings is not captured and thrown"
    (is (thrown-with-msg?
         #?(:clj Exception :cljs js/Error) #"^foo-exception$"
         (f/when-succ [x (throw (ex-info "foo-exception" {}))]
           false))))
  (testing "Exception on body is not captured and thrown"
    (is (thrown-with-msg?
         #?(:clj Exception :cljs js/Error) #"^foo-exception$"
         (f/when-succ [x 1]
           (throw (ex-info "foo-exception" {})))))))

(deftest when-fail-test
  (testing "Evaluates 'body' form if test is failed"
    (is (= (f/when-fail [x (f/fail ::foo-error)]
             @x)
           ::foo-error)))
  (testing "Returns nil if test is successful"
    (is (nil? (f/when-fail [x 1]
                false))))
  (testing "Exception on bindings is not captured and thrown"
    (is (thrown-with-msg?
         #?(:clj Exception :cljs js/Error) #"^foo-exception$"
         (f/when-fail [x (throw (ex-info "foo-exception" {}))]
           false))))
  (testing "Exception on body is not captured and thrown"
    (is (thrown-with-msg?
         #?(:clj Exception :cljs js/Error) #"^foo-exception$"
         (f/when-fail [x (f/fail ::foo-error)]
           (throw (ex-info "foo-exception" {})))))))

(deftest when-succ*-test
  (testing "Evaluates 'body' form if test is successful"
    (is (true? (f/when-succ* [x 1]
                 (number? x)))))
  (testing "Returns nil if test throws exception"
    (is (nil? (f/when-succ* [x (throw (ex-info "foo-exception" {}))]
                 false))))
  (testing "Returns nil if test is failed"
    (is (nil? (f/when-succ* [x (f/fail ::foo-error)]
                false))))
  (testing "Exception on body is captured and converted into failure"
    (let [x (f/when-succ* [x 1]
              (throw (ex-info "foo-exception" {})))]
      (is (f/fail? x))
      (is (= @x :jp.nijohando.failable/exceptionproof)))))

(deftest when-fail*-test
  (testing "Evaluates 'body' form if test is failed"
    (is (= (f/when-fail* [x (f/fail ::foo-error)]
             @x)
           ::foo-error)))
  (testing "Evaluates 'body' form if test throws exception"
    (is (= (f/when-fail* [x (throw (ex-info "foo-exception" {}))]
             @x)
           :jp.nijohando.failable/exceptionproof)))
  (testing "Returns nil if test is successful"
    (is (nil? (f/when-fail* [x 1]
                false))))
  (testing "Exception on body is captured and converted into failure"
    (let [x (f/when-fail* [x (f/fail ::foo-error)]
              (throw (ex-info "foo-exception" {})))]
      (is (f/fail? x))
      (is (= @x :jp.nijohando.failable/exceptionproof)))))

