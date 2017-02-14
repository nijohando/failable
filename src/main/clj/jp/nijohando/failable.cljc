(ns jp.nijohando.failable)

(deftype Failure [e]
  #?@(:cljs [cljs.core/IDeref
              (-deref [_] e)]
      :clj  [clojure.lang.IDeref
              (deref [_] e)]))

(defn throwable?
  [x]
  (instance? #?(:clj Throwable :cljs js/Error) x))

(defn failure?
  [x]
  (instance? Failure x))

(def success? (comp not failure?))

(defmulti fail (fn [x & xs]
                 (let [vtype (fn [v]
                               (cond
                                 (string? v) :string
                                 (throwable? v) :throwable
                                 (failure? v) :failure))]
                   (->> (concat [x] xs)
                        (map vtype)))))
(defmethod fail [:string]
  [message & _]
  (fail (ex-info message {})))
(defmethod fail [:string :throwable]
  [message & [throwable & _]]
  (fail (ex-info message {} throwable)))
(defmethod fail [:string :failure]
  [message & [failure & _]]
  (fail (ex-info message {} @failure)))
(defmethod fail [:throwable]
  [throwable]
  (Failure. throwable))

(defn guard
  ([x]
   (guard x "Failure rejected"))
  ([x msg]
   (when (failure? x)
     (throw @(fail msg x)))
     x))

#? (:clj [

(defmacro if-cljs
  [then else]
  (if (boolean (:ns &env)) then else))

(defmacro when-success
  [value & body]
  `(when (success? ~value)
     ~@body))

(defmacro when-failure
  [value & body]
  `(when (failure? ~value)
     ~@body))

(defmacro ftry
  [& forms]
  `(if-cljs
    (try
       ~@forms
       (catch js/Error e#
         (fail e#)))
    (try
       ~@forms
       (catch Exception e#
         (fail e#)))))

(defmacro flet
  [bindings & body]
  (->> (reverse (partition 2 bindings))
       (reduce (fn [acc [l r]]
                 `(let [~l (ftry ~r)]
                    (if (failure? ~l)
                      ~l
                      ~acc)))
               `(ftry ~@body))))

(defmacro f->
  "When expr is not failure, threads it into the first form (via ->),
  and when that result is not failure, through the next etc"
  [expr & body]
  (let [g (gensym)
        steps (map (fn [step]
                     `(if (failure? ~g)
                        ~g
                        (-> ~g
                            ~step)))
                   body)]
    `(ftry
       (let [~g ~expr
             ~@(interleave (repeat g) (butlast steps))]
         ~(if (empty? steps)
            g
            (last steps))))))

(defmacro f->>
  "When expr is not failure, threads it into the first form (via ->>),
  and when that result is not failure, through the next etc"
  [expr & body]
  (let [g (gensym)
        steps (map (fn [step]
                     `(if (failure? ~g)
                        ~g
                        (->> ~g
                             ~step)))
                   body)]
    `(ftry
       (let [~g ~expr
             ~@(interleave (repeat g) (butlast steps))]
         ~(if (empty? steps)
            g
            (last steps))))))
])



