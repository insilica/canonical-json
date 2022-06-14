;; NOTE: Used only for perf testing - this project is built with Maven (see pom.xml)
(defproject clojure.data.json "1.1.1-SNAPSHOT"
  :dependencies [[org.clojure/clojure "1.11.1"]]
  :source-paths ["src/main/clojure"]
  :java-source-paths ["src/main/java"]
  :java-test-paths ["src/test/java"]
  :test-paths ["src/test/clojure" "src/test/clojure-perf"]
  :profiles {:dev {:dependencies [[com.clojure-goes-fast/clj-async-profiler "0.5.1"]
                                  [com.clojure-goes-fast/clj-java-decompiler "0.3.1"]
                                  [org.clojure/test.check "1.1.1"]
                                  [criterium/criterium "0.4.6"]
                                  [metosin/jsonista "0.3.5"]
                                  [cheshire/cheshire "5.10.2"]
                                  [org.openjdk.jmh/jmh-core "1.34"]
                                  [jmh-clojure/jmh-clojure "0.4.1"]
                                  [com.jsoniter/jsoniter "0.9.23"]]
                   :resource-paths ["dev-resources"]
                   :global-vars {*warn-on-reflection* true}}}
  ;;:plugins [[lein-nodisassemble "0.1.3"]]
  :jvm-opts ["-Djdk.attach.allowAttachSelf=true"])
