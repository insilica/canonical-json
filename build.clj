(ns build
  (:refer-clojure :exclude [test])
  (:require [clojure.tools.build.api :as b]
            [org.corfield.build :as bb]))

(def lib 'co.insilica/canonical-json)
(def version "3.3.0")
(defn get-version [opts]
  (str version (when (:snapshot opts) "-SNAPSHOT")))

(defn test [opts]
  (b/process {:command-args ["lein" "test"]})
  opts)

(defn ci "Run the CI pipeline of tests (and build the JAR)." [opts]
  (-> opts
      (assoc :lib lib
             :src-dirs ["src/main/clojure"]
             :version (get-version opts))
      bb/clean
      test
      bb/clean
      (assoc :src-pom "template/pom.xml")
      bb/jar))

(defn deploy "Deploy the JAR to Clojars." [opts]
  (-> opts
      (assoc :lib lib :version (get-version opts))
      bb/deploy))
