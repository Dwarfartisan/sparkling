(ns sparkling.rdd.jdbc
  (:require [clojure.tools.logging :refer :all]
            [sparkling.scalaInterop :as scala :refer [function0 function1 OBJECT-CLASS-TAG]])
  (:import [java.sql ResultSet ResultSetMetaData]
           [org.apache.spark.rdd JdbcRDD]
           [org.apache.spark.api.java JavaRDD JavaSparkContext]))


(defn seq-from-countable [count value-fn]
  (let [^objects a (make-array Object count)]
    (dotimes [i count]
      (aset a i (value-fn (inc i))))
    a
    )
  #_(doall (for [i (range 1 (inc count))]
    (value-fn countable i))))
#_(loop [i count
       ret []]
  (if (pos? i)
    (recur (dec i) (cons (value-fn countable i) ret))
    ret))

;; TODO: Make this into tests

; (seq-from-countable nil (fn [_] 2) (fn [_ i] i))
; => (1 2)

;(seq-from-countable "test" (fn [x] (count x)) (fn [y i] [y i]))
;=> (["test" 1] ["test" 2] ["test" 3] ["test" 4])

;(seq-from-countable "test" 3 (fn [y i] [y i]))
;=> (["test" 1] ["test" 2] ["test" 3])



(defn mangle
  "Perform name-mangling. We want to read from avro files to and avro does not support dashes as field names.
  So it converts them back and forth to underscores. Thus, while reading, it converts everything to dashes.
  So, in order to get the same result, we would have to rename all entries or we should have a name mangling function for the jdbc-items to return everything 'dashified'."
  [^String n] (.replace n \_ \-))

(def clojurify (comp keyword mangle))

(defn get-columns [^ResultSetMetaData meta]
  (let [column-count (.getColumnCount meta)]
    (seq-from-countable column-count (fn [^long idx] (clojurify (.getColumnLabel meta idx))))))

(defn result-set-to-object-array [^ResultSet result-set]
              #_(JdbcRDD/resultSetToObjectArray result-set) ;; TODO: Returning only an array of the value is about half the time (8sec instead of 16sec) for viewsclicks data!

              (let [^ResultSetMetaData meta (.getMetaData result-set)]
                (zipmap (get-columns meta)
                        (seq-from-countable (.getColumnCount meta) (fn [^long idx] (.getObject result-set idx))))))

(defn load-jdbc [^JavaSparkContext sc get-connection query min max partitions] ;; TODO: Think about pushing name mangling to the interface of this function!
  (info "Defining a jdbc-rdd:" query min max partitions)
  (JavaRDD/fromRDD
    (JdbcRDD. (.sc sc)
              (scala/function0 get-connection)
              query
              (long min)
              (long max)
              (int partitions)
              (scala/function1 result-set-to-object-array)
              scala/OBJECT-CLASS-TAG)
    scala/OBJECT-CLASS-TAG))


;; (= #inst "2014-09-01T22:00:00.000-00:00" (.parse (java.text.SimpleDateFormat. "yyyy-MM-dd") "2014-09-02"))
