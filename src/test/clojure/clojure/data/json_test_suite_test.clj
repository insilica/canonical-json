(ns clojure.data.json-test-suite-test
  (:require [clojure.data.json :as json]
            [clojure.test :refer :all]
            [clojure.string :as str]))

(deftest i-number-double-huge-neg-exp-test
  (is (= [0.0] (json/read-str "[123.456e-789]"))))

(deftest i-number-huge-exp-test
  (is (= [##Inf]
         (json/read-str "[0.4e00669999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999969999999006]"))))

(deftest i-number-neg-int-huge-exp-test
  (is (= [##-Inf] (json/read-str "[-1e+9999]"))))

(deftest i-number-pos-double-huge-exp-test
  (is (= [##Inf] (json/read-str "[1.5e+9999]"))))

(deftest i-number-real-neg-overflow-test
  (is (= [##-Inf] (json/read-str "[-123123e100000]"))))

(deftest i-number-real-pos-overflow-test
  (is (= [##Inf] (json/read-str "[123123e100000]"))))

(deftest i-number-real-underflow-test
  (is (= [0.0] (json/read-str "[123e-10000000]"))))

(deftest i-number-too-big-neg-int-test
  (is (= [-123123123123123123123123123123N]
         (json/read-str "[-123123123123123123123123123123]"))))

(deftest i-number-too-big-pos-int-test
  (is (= [100000000000000000000N] (json/read-str "[100000000000000000000]"))))

(deftest i-number-very-big-negative-int-test
  (is (= [-237462374673276894279832749832423479823246327846N]
         (json/read-str "[-237462374673276894279832749832423479823246327846]"))))

(deftest n-number-++-test
  (is (thrown? Exception (json/read-str "[++1234]"))))

(deftest n-number-+1-test
  (is (thrown? Exception (json/read-str "[+1]"))))

(deftest n-number-+Inf-test
  (is (thrown? Exception (json/read-str "[+Inf]"))))

(deftest n-number--01-test
  (is (thrown? Exception (json/read-str "[-01]"))))

(deftest n-number--1.0.-test
  (is (thrown? Exception (json/read-str "[-1.0.]"))))

(deftest n-number--2.-test
  (is (thrown? Exception (json/read-str "[-2.]"))))

(deftest n-number--NaN-test
  (is (thrown? Exception (json/read-str "[-NaN]"))))

(deftest n-number-.-1-test
  (is (thrown? Exception (json/read-str "[.-1]"))))

(deftest n-number-.2e-3-test
  (is (thrown? Exception (json/read-str "[.2e-3]"))))

(deftest n-number-0-capital-E+-test
  (is (thrown? Exception (json/read-str "[0E+]"))))

(deftest n-number-0-capital-E-test
  (is (thrown? Exception (json/read-str "[0E]"))))

(deftest n-number-0.1.2-test
  (is (thrown? Exception (json/read-str "[0.1.2]"))))

(deftest n-number-0.3e+-test
  (is (thrown? Exception (json/read-str "[0.3e+]"))))

(deftest n-number-0.3e-test
  (is (thrown? Exception (json/read-str "[0.3e]"))))

(deftest n-number-0.e1-test
  (is (thrown? Exception (json/read-str "[0.e1]"))))

(deftest n-number-0e+-test
  (is (thrown? Exception (json/read-str "[0e+]"))))

(deftest n-number-0e-test
  (is (thrown? Exception (json/read-str "[0e]"))))

(deftest n-number-1-000-test
  (is (thrown? Exception (json/read-str "[1 000.0]"))))

(deftest n-number-1.0e+-test
  (is (thrown? Exception (json/read-str "[1.0e+]"))))

(deftest n-number-1.0e--test
  (is (thrown? Exception (json/read-str "[1.0e-]"))))

(deftest n-number-1.0e-test
  (is (thrown? Exception (json/read-str "[1.0e]"))))

(deftest n-number-1eE2-test
  (is (thrown? Exception (json/read-str "[1eE2]"))))

(deftest n-number-2.e+3-test
  (is (thrown? Exception (json/read-str "[2.e+3]"))))

(deftest n-number-2.e-3-test
  (is (thrown? Exception (json/read-str "[2.e-3]"))))

(deftest n-number-2.e3-test
  (is (thrown? Exception (json/read-str "[2.e3]"))))

(deftest n-number-9.e+-test
  (is (thrown? Exception (json/read-str "[9.e+]"))))

(deftest n-number-Inf-test
  (is (thrown? Exception (json/read-str "[Inf]"))))

(deftest n-number-NaN-test
  (is (thrown? Exception (json/read-str "[NaN]"))))

(deftest n-number-expression-test
  (is (thrown? Exception (json/read-str "[1+2]"))))

(deftest n-number-hex-1-digit-test
  (is (thrown? Exception (json/read-str "[0x1]"))))

(deftest n-number-hex-2-digits-test
  (is (thrown? Exception (json/read-str "[0x42]"))))

(deftest n-number-infinity-test
  (is (thrown? Exception (json/read-str "[Infinity]"))))

(deftest n-number-invalid+--test
  (is (thrown? Exception (json/read-str "[0e+-1]"))))

(deftest n-number-invalid-negative-real-test
  (is (thrown? Exception (json/read-str "[-123.123foo]"))))

(deftest n-number-minus-infinity-test
  (is (thrown? Exception (json/read-str "[-Infinity]"))))

(deftest n-number-minus-sign-with-trailing-garbage-test
  (is (thrown? Exception (json/read-str "[-foo]"))))

(deftest n-number-minus-space-1-test
  (is (thrown? Exception (json/read-str "[- 1]"))))

(deftest n-number-neg-int-starting-with-zero-test
  (is (thrown? Exception (json/read-str "[-012]"))))

(deftest n-number-neg-real-without-int-part-test
  (is (thrown? Exception (json/read-str "[-.123]"))))

(deftest n-number-neg-with-garbage-at-end-test
  (is (thrown? Exception (json/read-str "[-1x]"))))

(deftest n-number-real-garbage-after-e-test
  (is (thrown? Exception (json/read-str "[1ea]"))))

(deftest n-number-real-without-fractional-part-test
  (is (thrown? Exception (json/read-str "[1.]"))))

(deftest n-number-starting-with-dot-test
  (is (thrown? Exception (json/read-str "[.123]"))))

(deftest n-number-with-alpha-char-test
  (is (thrown? Exception (json/read-str "[1.8011670033376514H-308]"))))

(deftest n-number-with-alpha-test
  (is (thrown? Exception (json/read-str "[1.2a-3]"))))

(deftest n-number-with-leading-zero-test
  (is (thrown? Exception (json/read-str "[012]"))))

(deftest n-object-non-string-key-but-huge-number-instead-test
  (is (thrown? Exception (json/read-str "{9999E9999:1}"))))

(deftest n-structure-number-with-trailing-garbage-test
  (is (thrown? Exception (json/read-str "2@"))))

(deftest y-number-0e+1-test
  (is (= [0.0] (json/read-str "[0e+1]"))))

(deftest y-number-0e1-test
  (is (= [0.0] (json/read-str "[0e1]"))))

(deftest y-number-after-space-test
  (is (= [4] (json/read-str "[ 4]"))))

(deftest y-number-double-close-to-zero-test
  (is (= [-1.0E-78]
         (json/read-str "[-0.000000000000000000000000000000000000000000000000000000000000000000000000000001]"))))

(deftest y-number-int-with-exp-test
  (is (= [200.0] (json/read-str "[20e1]"))))

(deftest y-number-minus-zero-test
  (is (= [0] (json/read-str "[-0]"))))

(deftest y-number-negative-int-test
  (is (= [-123] (json/read-str "[-123]"))))

(deftest y-number-negative-one-test
  (is (= [-1] (json/read-str "[-1]"))))

(deftest y-number-negative-zero-test
  (is (= [0] (json/read-str "[-0]"))))

(deftest y-number-real-capital-e-neg-exp-test
  (is (= [0.01] (json/read-str "[1E-2]"))))

(deftest y-number-real-capital-e-pos-exp-test
  (is (= [100.0] (json/read-str "[1E+2]"))))

(deftest y-number-real-capital-e-test
  (is (= [1.0E22] (json/read-str "[1E22]"))))

(deftest y-number-real-exponent-test
  (is (= [1.23E47] (json/read-str "[123e45]"))))

(deftest y-number-real-fraction-exponent-test
  (is (= [1.23456E80] (json/read-str "[123.456e78]"))))

(deftest y-number-real-neg-exp-test
  (is (= [0.01] (json/read-str "[1e-2]"))))

(deftest y-number-real-pos-exponent-test
  (is (= [100.0] (json/read-str "[1e+2]"))))

(deftest y-number-simple-int-test
  (is (= [123] (json/read-str "[123]"))))

(deftest y-number-simple-real-test
  (is (= [123.456789] (json/read-str "[123.456789]"))))

(deftest y-number-test
  (is (= [1.23E67] (json/read-str "[123e65]"))))

(deftest y-object-extreme-numbers-test
  (is (= {"min" -1.0E28, "max" 1.0E28}
         (json/read-str "{\"min\": -1.0e+28, \"max\": 1.0e+28}"))))
