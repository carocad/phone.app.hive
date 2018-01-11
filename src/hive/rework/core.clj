(ns hive.rework.core)

(defn- throw-err
  [value]
  (if (instance? js/Error value)
    (throw value)
    value))

(defmacro <?
  "Like <! but throws errors."
  [port]
  `(throw-err (cljs.core.async/<! ~port)))