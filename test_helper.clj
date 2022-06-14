#! /usr/bin/env bb

(require '[clojure.java.io :as io])

(load-file "../src/main/clojure/insilica/canonical_json.clj")
(require '[insilica.canonical-json :as json])

(with-open [reader (-> *command-line-args* first io/file io/reader)]
  (-> reader
      json/read
      (json/write *out*)))

nil
