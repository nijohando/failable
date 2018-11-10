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
    (let [root (loop [f x]
                 (if (succ? f)
                   f
                   (recur (cause f))))]
      (throw (ex-info
              (str "Failed to ensure value"
                   (when-some [reason @x]
                     (str " by " reason)))
              (->> (dissoc x ::reason)
                   (into {}))
              root)))
    x))

(defmacro do*
  [& forms]
  `(if-cljs
    (try
      ~@forms
      (catch js/Error e#
        (-> (fail ::exception)
            (assoc ::cause e#))))
    (try
      ~@forms
      (catch Exception e#
        (-> (fail ::exception)
            (assoc ::cause e#))))))

(defmacro -pred-let
  [pred bindings & body]
  (->> (reverse (partition 2 bindings))
       (reduce (fn [acc [l r]]
                 `(let [x# ~r
                        ~l x#]
                    (if (~pred x#)
                      ~acc
                      x#)))
               `(do ~@body))))

(defmacro -pred-let*
  [pred bindings & body]
  (let [bindings* (->> (partition 2 bindings)
                       (map (fn [[l r]]
                              [l `(do* ~r)]))
                       (apply concat))]
    `(-pred-let ~pred [~@bindings*] (do* ~@body))))

(defmacro slet
  [bindings & body]
  `(-pred-let succ? ~bindings ~@body))

(defmacro slet*
  [bindings & body]
  `(-pred-let* succ? ~bindings ~@body))

(defmacro flet
  [bindings & body]
  `(-pred-let fail? ~bindings ~@body))

(defmacro flet*
  [bindings & body]
  `(-pred-let* fail? ~bindings ~@body))

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

(defmacro -as-pred->
  [pred expr name & forms]
  `(-pred-let ~pred [~name ~expr
                     ~@(interleave (repeat name) (butlast forms))]
              ~(if (empty? forms)
                 name
                 (last forms))))

(defmacro -as-pred->*
  [pred expr name & forms]
  `(-pred-let ~pred [~name (do* ~expr)
                     ~@(interleave (repeat name) (->> (butlast forms)
                                                      (map (fn [form] `(do* ~form)))))]
              (do*
                ~(if (empty? forms)
                   name
                   (last forms)))))

(defmacro as-succ->
  [expr name & forms]
  `(-as-pred-> succ? ~expr ~name ~@forms))

(defmacro as-succ->*
  [expr name & forms]
  `(-as-pred->* succ? ~expr ~name ~@forms))

(defmacro as-fail->
  [expr name & forms]
  `(-as-pred-> fail? ~expr ~name ~@forms))

(defmacro as-fail->*
  [expr name & forms]
  `(-as-pred->* fail? ~expr ~name ~@forms))

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

#?(:clj
   (do
     (prefer-method print-method clojure.lang.IRecord clojure.lang.IDeref)
     (prefer-method print-method java.util.Map clojure.lang.IDeref)
     (prefer-method print-method clojure.lang.IPersistentMap clojure.lang.IDeref)))
