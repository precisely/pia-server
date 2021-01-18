(ns pia-server.util)


(defn remove-nil-values [m]
  (into {} (filter #(let [[_ v] %] v) m)))

(defn nilable [p o] (or (nil? o) (p o)))
