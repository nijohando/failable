(ns jp.nijohando.failable
  (:refer-clojure :exclude [ensure]))

(defmacro if-cljs
  [then else]
  (if (boolean (:ns &env)) then else))

(defrecord Failure []
  #?@(:clj [clojure.lang.IDeref
            (deref [this] (::reason this))]
      :cljs [cljs.core/IDeref
             (-deref [this] (::reason this))]))

(defn fail
  ([]
   (Failure.))
  ([reason]
   (-> (Failure.)
       (assoc ::reason reason))))

(defn fail?
  [x]
  (instance? Failure x))

(defn succ?
  [x]
  (not (fail? x)))

(defn reason
  [x]
  (when (fail? x)
    (::reason x)))

(defn cause
  [x]
  (when (fail? x)
    (::cause x)))

(defn wrap
  [x reason]
  (-> (fail reason)
      (assoc ::cause x)))

(defn ensure
  [x]
  (if (fail? x)
    (throw (ex-info "Failed to ensure value" {:failure x}))
    x))

(defmacro do*
  [& forms]
  `(if-cljs
    (try
      ~@forms
      (catch js/Error e#
        (-> (fail :exception)
            (assoc ::cause e#))))
    (try
      ~@forms
      (catch Exception e#
        (-> (fail :exception)
            (assoc ::cause e#))))))

(defmacro flet
  [bindings & body]
  (->> (reverse (partition 2 bindings))
       (reduce (fn [acc [l r]]
                 `(let [x# ~r
                        ~l x#]
                    (if (fail? x#)
                      x#
                      ~acc)))
               `(do ~@body))))

(defmacro flet*
  [bindings & body]
  (let [bindings* (->> (partition 2 bindings)
                       (map (fn [[l r]]
                              [l `(do* ~r)]))
                       (apply concat))]
    `(flet [~@bindings*]
           (do* ~@body))))

(defmacro succ->
  [expr & body]
  (let [g (gensym)
        steps (map (fn [step]
                     `(if (fail? ~g)
                        ~g
                        (-> ~g
                            ~step)))
                   body)]
    `(let [~g ~expr
           ~@(interleave (repeat g) (butlast steps))]
       ~(if (empty? steps)
          g
          (last steps)))))

(defmacro succ->*
  [expr & body]
  (let [g (gensym)
        steps (map (fn [step]
                     `(if (fail? ~g)
                        ~g
                        (-> ~g
                            ~step)))
                   body)]
    `(do*
      (let [~g ~expr
            ~@(interleave (repeat g) (butlast steps))]
        ~(if (empty? steps)
           g
           (last steps))))))

(defmacro succ->>
  [expr & body]
  (let [g (gensym)
        steps (map (fn [step]
                     `(if (fail? ~g)
                        ~g
                        (->> ~g
                             ~step)))
                   body)]
    `(let [~g ~expr
           ~@(interleave (repeat g) (butlast steps))]
       ~(if (empty? steps)
          g
          (last steps)))))

(defmacro succ->>*
  [expr & body]
  (let [g (gensym)
        steps (map (fn [step]
                     `(if (fail? ~g)
                        ~g
                        (->> ~g
                             ~step)))
                   body)]
    `(do*
      (let [~g ~expr
            ~@(interleave (repeat g) (butlast steps))]
        ~(if (empty? steps)
           g
           (last steps))))))

(defmacro fail->
  [expr & body]
  (let [g (gensym)
        steps (map (fn [step]
                     `(if (succ? ~g)
                        ~g
                        (-> ~g
                            ~step)))
                   body)]
    `(let [~g ~expr
           ~@(interleave (repeat g) (butlast steps))]
       ~(if (empty? steps)
          g
          (last steps)))))

(defmacro fail->*
  [expr & body]
  (let [g (gensym)
        steps (map (fn [step]
                     `(if (succ? ~g)
                        ~g
                        (do*
                          (-> ~g
                              ~step))))
                   body)]
    `(do*
       (let [~g ~expr
             ~@(interleave (repeat g) (butlast steps))]
         ~(if (empty? steps)
            g
            (last steps))))))

(defmacro fail->>
  [expr & body]
  (let [g (gensym)
        steps (map (fn [step]
                     `(if (succ? ~g)
                        ~g
                        (->> ~g
                             ~step)))
                   body)]
    `(let [~g ~expr
           ~@(interleave (repeat g) (butlast steps))]
       ~(if (empty? steps)
          g
          (last steps)))))

(defmacro fail->>*
  [expr & body]
  (let [g (gensym)
        steps (map (fn [step]
                     `(if (succ? ~g)
                        ~g
                        (do*
                          (->> ~g
                               ~step))))
                   body)]
    `(do*
       (let [~g ~expr
             ~@(interleave (repeat g) (butlast steps))]
         ~(if (empty? steps)
            g
            (last steps))))))

(defmacro if-succ
  ([bindings then]
   `(if-succ ~bindings ~then nil))
  ([bindings then else]
   (let [l (bindings 0)
         r (bindings 1)
         g (gensym)]
     `(let [~g ~r
            ~l ~g]
        (if (succ? ~g)
          ~then
          ~(when (some? else)
             else))))))

(defmacro if-succ*
  ([bindings then]
   `(if-succ* ~bindings ~then nil))
  ([bindings then else]
   (let [l (bindings 0)
         r (bindings 1)
         g (gensym)]
     `(let [~g (do* ~r)
            ~l ~g]
        (do*
         (if (succ? ~g)
           ~then
           ~(when (some? else)
              else)))))))

(defmacro if-fail
  ([bindings then]
   `(if-fail ~bindings ~then nil))
  ([bindings then else]
   (let [l (bindings 0)
         r (bindings 1)
         g (gensym)]
     `(let [~g ~r
            ~l ~g]
        (if (fail? ~g)
          ~then
          ~(when (some? else)
             else))))))

(defmacro if-fail*
  ([bindings then]
   `(if-fail* ~bindings ~then nil))
  ([bindings then else]
   (let [l (bindings 0)
         r (bindings 1)
         g (gensym)]
     `(let [~g (do* ~r)
            ~l ~g]
        (do*
         (if (fail? ~g)
           ~then
           ~(when (some? else)
              else)))))))

(defmacro when-succ
  [bindings & body]
  `(if-succ ~bindings (do ~@body)))

(defmacro when-succ*
  [bindings & body]
  `(if-succ* ~bindings (do ~@body)))

(defmacro when-fail
  [bindings & body]
  `(if-fail ~bindings (do ~@body)))

(defmacro when-fail*
  [bindings & body]
  `(if-fail* ~bindings (do ~@body)))

#?(:clj (prefer-method print-method clojure.lang.IRecord clojure.lang.IDeref))

