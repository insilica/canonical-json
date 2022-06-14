;; Copyright (c) Stuart Sierra, 2012. All rights reserved.  The use
;; and distribution terms for this software are covered by the Eclipse
;; Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this
;; distribution.  By using this software in any fashion, you are
;; agreeing to be bound by the terms of this license.  You must not
;; remove this notice, or any other, from this software.

(ns ^{:author "Stuart Sierra"
      :doc "JavaScript Object Notation (JSON) parser/generator.
  See http://www.json.org/"}
  insilica.canonical-json
  (:refer-clojure :exclude (read))
  (:require [clojure.pprint :as pprint]
            [clojure.string :as str])
  (:import (java.io PrintWriter PushbackReader StringWriter
                    Writer StringReader EOFException)))

;;; JSON READER

(set! *warn-on-reflection* true)

(defn- default-write-key-fn
  [x]
  (cond (instance? clojure.lang.Named x)
        (name x)
        (nil? x)
        (throw (Exception. "JSON object properties may not be nil"))
        :else (str x)))

(defn- default-value-fn [k v] v)

(declare -read)

(defmacro ^:private codepoint [c]
  (int c))

(defn- codepoint-clause [[test result]]
  (cond (list? test)
        [(map int test) result]
        (= test :whitespace)
        ['(9 10 13 32) result]
        (= test :js-separators)
        ['(16r2028 16r2029) result]
        :else
        [(int test) result]))

(defmacro ^:private codepoint-case [e & clauses]
  `(case ~e
     ~@(mapcat codepoint-clause (partition 2 clauses))
     ~@(when (odd? (count clauses))
         [(last clauses)])))

(defn- read-hex-char [^PushbackReader stream]
  ;; Expects to be called with the head of the stream AFTER the
  ;; initial "\u".  Reads the next four characters from the stream.
  (let [a (.read stream)
        b (.read stream)
        c (.read stream)
        d (.read stream)]
    (when (or (neg? a) (neg? b) (neg? c) (neg? d))
      (throw (EOFException.
              "JSON error (end-of-file inside Unicode character escape)")))
    (let [s (str (char a) (char b) (char c) (char d))]
      (char (Integer/parseInt s 16)))))

(defn- read-escaped-char [^PushbackReader stream]
  ;; Expects to be called with the head of the stream AFTER the
  ;; initial backslash.
  (let [c (.read stream)]
    (when (neg? c)
      (throw (EOFException. "JSON error (end-of-file inside escaped char)")))
    (codepoint-case c
      (\" \\ \/) (char c)
      \b \backspace
      \f \formfeed
      \n \newline
      \r \return
      \t \tab
      \u (read-hex-char stream))))

(defn- slow-read-string [^PushbackReader stream ^String already-read]
  (let [buffer (StringBuilder. already-read)]
    (loop []
      (let [c (.read stream)]
        (when (neg? c)
          (throw (EOFException. "JSON error (end-of-file inside string)")))
        (codepoint-case c
          \" (str buffer)
          \\ (do (.append buffer (read-escaped-char stream))
                 (recur))
          (do (.append buffer (char c))
              (recur)))))))

(defn- read-quoted-string [^PushbackReader stream]
  ;; Expects to be called with the head of the stream AFTER the
  ;; opening quotation mark.
  (let [buffer ^chars (char-array 64)
        read (.read stream buffer 0 64)
        end-index (unchecked-dec-int read)]
    (when (neg? read)
      (throw (EOFException. "JSON error (end-of-file inside string)")))
    (loop [i (int 0)]
      (let [c (int (aget buffer i))]
        (codepoint-case c
          \" (let [off (unchecked-inc-int i)
                   len (unchecked-subtract-int read off)]
               (.unread stream buffer off len)
               (String. buffer 0 i))
          \\ (let [off i
                   len (unchecked-subtract-int read off)]
               (.unread stream buffer off len)
               (slow-read-string stream (String. buffer 0 i)))
          (if (= i end-index)
            (do (.unread stream c)
                (slow-read-string stream (String. buffer 0 i)))
            (recur (unchecked-inc-int i))))))))

(defn- read-integer [^String string]
  (.stripTrailingZeros (bigdec string)))

(defn- read-decimal [^String string]
  (.stripTrailingZeros (bigdec string)))

(defn- read-number [^PushbackReader stream]
  (let [buffer (StringBuilder.)
        decimal? (loop [stage :minus]
                   (let [c (.read stream)]
                     (case stage
                       :minus
                       (codepoint-case c
                         \-
                         (do (.append buffer (char c))
                             (recur :int-zero))
                         \0
                         (do (.append buffer (char c))
                             (recur :frac-point))
                         (\1 \2 \3 \4 \5 \6 \7 \8 \9)
                         (do (.append buffer (char c))
                             (recur :int-digit))
                         (throw (Exception. "JSON error (invalid number literal)")))
                       ;; Number must either be a single 0 or 1-9 followed by 0-9
                       :int-zero
                       (codepoint-case c
                         \0
                         (do (.append buffer (char c))
                             (recur :frac-point))
                         (\1 \2 \3 \4 \5 \6 \7 \8 \9)
                         (do (.append buffer (char c))
                             (recur :int-digit))
                         (throw (Exception. "JSON error (invalid number literal)")))
                       ;; at this point, there is at least one digit
                       :int-digit
                       (codepoint-case c
                         (\0 \1 \2 \3 \4 \5 \6 \7 \8 \9)
                         (do (.append buffer (char c))
                             (recur :int-digit))
                         \.
                         (do (.append buffer (char c))
                             (recur :frac-first))
                         (\e \E)
                         (do (.append buffer (char c))
                             (recur :exp-symbol))
                         ;; early exit
                         :whitespace
                         (do (.unread stream c)
                             false)
                         (\, \] \} -1)
                         (do (.unread stream c)
                             false)
                         (throw (Exception. "JSON error (invalid number literal)")))
                       ;; previous character is a "0"
                       :frac-point
                       (codepoint-case c
                         \.
                         (do (.append buffer (char c))
                             (recur :frac-first))
                         (\e \E)
                         (do (.append buffer (char c))
                             (recur :exp-symbol))
                         ;; early exit
                         :whitespace
                         (do (.unread stream c)
                             false)
                         (\, \] \} -1)
                         (do (.unread stream c)
                             false)
                         ;; Disallow zero-padded numbers or invalid characters
                         (throw (Exception. "JSON error (invalid number literal)")))
                       ;; previous character is a "."
                       :frac-first
                       (codepoint-case c
                         (\0 \1 \2 \3 \4 \5 \6 \7 \8 \9)
                         (do (.append buffer (char c))
                             (recur :frac-digit))
                         (throw (Exception. "JSON error (invalid number literal)")))
                       ;; any number of following digits
                       :frac-digit
                       (codepoint-case c
                         (\0 \1 \2 \3 \4 \5 \6 \7 \8 \9)
                         (do (.append buffer (char c))
                             (recur :frac-digit))
                         (\e \E)
                         (do (.append buffer (char c))
                             (recur :exp-symbol))
                         ;; early exit
                         :whitespace
                         (do (.unread stream c)
                             true)
                         (\, \] \} -1)
                         (do (.unread stream c)
                             true)
                         (throw (Exception. "JSON error (invalid number literal)")))
                       ;; previous character is a "e" or "E"
                       :exp-symbol
                       (codepoint-case c
                         (\- \+)
                         (do (.append buffer (char c))
                             (recur :exp-first))
                         (\0 \1 \2 \3 \4 \5 \6 \7 \8 \9)
                         (do (.append buffer (char c))
                             (recur :exp-digit)))
                       ;; previous character is a "-" or "+"
                       ;; must have at least one digit
                       :exp-first
                       (codepoint-case c
                         (\0 \1 \2 \3 \4 \5 \6 \7 \8 \9)
                         (do (.append buffer (char c))
                             (recur :exp-digit))
                         (throw (Exception. "JSON error (invalid number literal)")))
                       ;; any number of following digits
                       :exp-digit
                       (codepoint-case c
                         (\0 \1 \2 \3 \4 \5 \6 \7 \8 \9)
                         (do (.append buffer (char c))
                             (recur :exp-digit))
                         :whitespace
                         (do (.unread stream c)
                             true)
                         (\, \] \} -1)
                         (do (.unread stream c)
                             true)
                         (throw (Exception. "JSON error (invalid number literal)"))))))]
    (if decimal?
      (read-decimal (str buffer))
      (read-integer (str buffer)))))

(defn- next-token [^PushbackReader stream]
  (loop [c (.read stream)]
    (if (< 32 c)
      (int c)
      (codepoint-case (int c)
        :whitespace (recur (.read stream))
        (int c)))))

(defn invalid-array-exception []
  (Exception. "JSON error (invalid array)"))

(defn- read-array* [^PushbackReader stream options]
  ;; Handles all array values after the first.
  (loop [result (transient [])]
    (let [r (conj! result (-read stream true nil options))]
      (codepoint-case (int (next-token stream))
        \] (persistent! r)
        \, (recur r)
        (throw (invalid-array-exception))))))

(defn- read-array [^PushbackReader stream options]
  ;; Expects to be called with the head of the stream AFTER the
  ;; opening bracket.
  ;; Only handles array value.
  (let [c (int (next-token stream))]
    (codepoint-case c
      \] []
      \, (throw (invalid-array-exception))
      -1 (throw (Exception. "JSON error (end-of-file inside array)"))
      (do (.unread stream c)
          (read-array* stream options)))))

(defn- read-key [^PushbackReader stream]
  (let [c (int (next-token stream))]
    (if (= c (codepoint \"))
      (let [key (read-quoted-string stream)]
        (if (= (codepoint \:) (int (next-token stream)))
          key
          (throw (Exception. "JSON error (missing `:` in object)"))))
      (codepoint-case c
        \} nil
        -1 (throw (Exception. "JSON error (end-of-file inside object)"))
        (throw (Exception. (str "JSON error (non-string key in object), found `" (char c) "`, expected `\"`")))))))

(defn- read-object [^PushbackReader stream options]
  ;; Expects to be called with the head of the stream AFTER the
  ;; opening bracket.
  (let [key-fn (get options :key-fn)
        value-fn (get options :value-fn)]
    (loop [result (transient {})]
      (if-let [key (read-key stream)]
        (let [key (cond-> key key-fn key-fn)
              value (-read stream true nil options)
              r (if value-fn
                  (let [out-value (value-fn key value)]
                    (if-not (= value-fn out-value)
                      (assoc! result key out-value)
                      result))
                  (assoc! result key value))]
          (codepoint-case (int (next-token stream))
            \, (recur r)
            \} (persistent! r)
            -1 (throw (Exception. "JSON error (end-of-file inside object)"))
            (throw (Exception. "JSON error (missing entry in object)"))))
        (let [r (persistent! result)]
          (if (empty? r)
            r
            (throw (Exception. "JSON error empty entry in object is not allowed"))))))))

(defn- -read
  [^PushbackReader stream eof-error? eof-value options]
  (let [c (int (next-token stream))]
    (codepoint-case c
        ;; Read numbers
        (\- \0 \1 \2 \3 \4 \5 \6 \7 \8 \9)
        (do (.unread stream c)
            (read-number stream))

        ;; Read strings
        \" (read-quoted-string stream)

        ;; Read null as nil
        \n (if (and (= (codepoint \u) (.read stream))
                    (= (codepoint \l) (.read stream))
                    (= (codepoint \l) (.read stream)))
             nil
             (throw (Exception. "JSON error (expected null)")))

        ;; Read true
        \t (if (and (= (codepoint \r) (.read stream))
                    (= (codepoint \u) (.read stream))
                    (= (codepoint \e) (.read stream)))
             true
             (throw (Exception. "JSON error (expected true)")))

        ;; Read false
        \f (if (and (= (codepoint \a) (.read stream))
                    (= (codepoint \l) (.read stream))
                    (= (codepoint \s) (.read stream))
                    (= (codepoint \e) (.read stream)))
             false
             (throw (Exception. "JSON error (expected false)")))

        ;; Read JSON objects
        \{ (read-object stream options)

        ;; Read JSON arrays
        \[ (read-array stream options)

        (if (neg? c) ;; Handle end-of-stream
          (if eof-error?
            (throw (EOFException. "JSON error (end-of-file)"))
            eof-value)
          (throw (Exception.
                  (str "JSON error (unexpected character): " (char c))))))))

(def default-read-options {:key-fn nil
                           :value-fn nil})
(defn read
  "Reads a single item of JSON data from a java.io.Reader. Options are
  key-value pairs, valid options are:

     :eof-error? boolean

        If true (default) will throw exception if the stream is empty.

     :eof-value Object

        Object to return if the stream is empty and eof-error? is
        false. Default is nil.

     :key-fn function

        Single-argument function called on JSON property names; return
        value will replace the property names in the output. Default
        is clojure.core/identity, use clojure.core/keyword to get
        keyword properties.

     :throw-on-extra-input? boolean

       If true, will throw an exception if the stream is not empty after
       reading one item.

     :value-fn function

        Function to transform values in maps (\"objects\" in JSON) in
        the output. For each JSON property, value-fn is called with
        two arguments: the property name (transformed by key-fn) and
        the value. The return value of value-fn will replace the value
        in the output. If value-fn returns itself, the property will
        be omitted from the output. The default value-fn returns the
        value unchanged. This option does not apply to non-map
        collections."
  [reader & {:as options}]
  (let [{:keys [eof-error? eof-value throw-on-extra-input?]
         :or {eof-error? true}} options
        reader (PushbackReader. reader 64)]
    (as-> options $
         (merge default-read-options $)
         (-read reader eof-error? eof-value $)
         (if-not throw-on-extra-input?
           $
           (let [c (int (next-token reader))]
             (if (= -1 c)
               $
               (do
                 (.unread reader c)
                 (throw (ex-info "JSON error (found extra input after reading item)"
                          {:item $})))))))))

(defn read-str
  "Reads one JSON value from input String. Options are the same as for
  read."
  [string & {:as options}]
  (read (StringReader. string) options))

;;; JSON WRITER


(defprotocol JSONWriter
  (-write [object out options]
    "Print object to Appendable out as JSON"))

(defn- ->hex-string [^Appendable out cp]
  (let [cpl (long cp)]
    (.append out "\\u")
    (cond
      (< cpl 16)
      (.append out "000")
      (< cpl 256)
      (.append out "00")
      (< cpl 4096)
      (.append out "0"))
    (->> cp
         Integer/toHexString
         str/upper-case
         (.append out))))

(def ^{:tag "[S"} codepoint-decoder
  (let [shorts (short-array 128)]
    (dotimes [i 128]
      (codepoint-case i
        \" (aset shorts i (short 1))
        \\ (aset shorts i (short 1))
        \/ (aset shorts i (short 2))
        \backspace (aset shorts i (short 3))
        \formfeed  (aset shorts i (short 4))
        \newline   (aset shorts i (short 5))
        \return    (aset shorts i (short 6))
        \tab       (aset shorts i (short 7))
        (if (< i 32)
          (aset shorts i (short 8))
          (aset shorts i (short 0)))))
    shorts))

(defn char-seq
  "Return a seq of the characters in a string, making sure not to split up
  UCS-2 (or is it UTF-16?) surrogate pairs. Because JavaScript. And Java.

  Based on https://lambdaisland.com/blog/12-06-2017-clojure-gotchas-surrogate-pairs
  Modified to handle lone surrogates, which are valid in JSON strings."
  ([s]
   (char-seq s 0))
  ([s offset]
   (when (< offset (count s))
     (let [code (.charAt s offset)
           width (if (<= 0xD800 (int code) 0xDBFF) 2 1)] ; detect "high surrogate"
       (cons (subs s offset (min (+ offset width) (count s)))
             (char-seq s (+ offset width)))))))

(defn- write-string [^CharSequence s ^Appendable out options]
  (let [decoder codepoint-decoder]
    (.append out \")
    (doseq [ch (char-seq s)]
      (let [cp (-> ch first .charValue int)
            cp2 (some-> ch second .charValue int)]
        (cond
          ; A surrogate pair
          (and cp2 (Character/isLowSurrogate (char cp2)))
          (.append out ch)

          ; Lone surrogate
          (Character/isSurrogate (char cp))
          (do (->hex-string out cp)
              (some->> cp2 (->hex-string out)))

          (< cp 128)
          (case (aget decoder cp)
            0 (.append out (char cp))
            1 (do (.append out (char (codepoint \\))) (.append out (char cp)))
            2 (.append out "/")
            3 (.append out "\\b")
            4 (.append out "\\f")
            5 (.append out "\\n")
            6 (.append out "\\r")
            7 (.append out "\\t")
            8 (->hex-string out cp))

          :else
          (.append out ch))))
    (.append out \")))

(defn- write-indent [^Appendable out options]
  (let [indent-depth (:indent-depth options)]
    (.append out \newline)
    (loop [i indent-depth]
      (when (pos? i)
        (.append out "  ")
        (recur (dec i))))))

;; https://github.com/simon-greatrix/CanonicalJson/blob/3ac58b5b44a1d6e768d8e5d3812cf1ac93adc6a5/src/main/java/io/setl/json/CJObject.java#L104-L123
(defn compare-by-codepoint [^String s1 ^String s2]
  (let [lim (min (count s1) (count s2))]
    (loop [i 0]
      (if (<= lim i)
        (- (count s1) (count s2))
        (let [cp1 (int (.codePointAt s1 i))
              cp2 (int (.codePointAt s2 i))]
          (cond
            (not= cp1 cp2) (- cp1 cp2)
            (> cp1 0xffff) (recur (+ 2 i))
            :else (recur (inc i))))))))

(defn- write-object [m ^Appendable out options]
  (let [key-fn (get options :key-fn)
        value-fn (get options :value-fn)
        indent (get options :indent)
        opts (cond-> options
               indent (update :indent-depth inc))]
    (.append out \{)
    (when (and indent (seq m))
      (write-indent out opts))
    (loop [x (->> m
                  (map (fn [[k v]]
                         [(key-fn k) (value-fn k v)]))
                  (into (sorted-map-by compare-by-codepoint)))
           have-printed-kv false]
      (when (seq x)
        (let [[out-key out-value] (first x)
              nxt (next x)]
          (when-not (string? out-key)
            (throw (Exception. "JSON object keys must be strings")))
          (if-not (= value-fn out-value)
            (do
              (when have-printed-kv
                (.append out \,)
                (when indent
                  (write-indent out opts)))
              (write-string out-key out opts)
              (.append out \:)
              (when indent
                (.append out \space))
              (-write out-value out opts)
              (when (seq nxt)
                (recur nxt true)))
            (when (seq nxt)
              (recur nxt have-printed-kv))))))
    (when (and indent (seq m))
      (write-indent out options)))
  (.append out \}))

(defn- write-array [s ^Appendable out options]
  (let [indent (get options :indent)
        opts (cond-> options
                  indent (update :indent-depth inc))]
    (.append out \[)
    (when (and indent (seq s))
      (write-indent out opts))
    (loop [x s]
      (when (seq x)
        (let [fst (first x)
              nxt (next x)]
          (-write fst out opts)
          (when (seq nxt)
            (.append out \,)
            (when indent
              (write-indent out opts))
            (recur nxt)))))
    (when (and indent (seq s))
      (write-indent out options)))
  (.append out \]))

(def re-sci-notation #"(-?\d+)\.?(\d+)?(E\+?\-?\d+)?")

(defn- format-bigdec [^BigDecimal x]
  (let [s (str x)
        [_ intpart decpart exp] (re-find re-sci-notation s)
        decpart (or decpart "0")
        neg? (str/starts-with? intpart "-")
        expval (->> (or exp "E0") rest (apply str) parse-long)]
    (if (some-> intpart parse-long zero?)
      (let [[_ leading-zeroes numpart] (re-find #"(0*)(\d+)" decpart)]
        (str (when neg? "-") (first numpart)
             "." (if (< 1 (count numpart)) (subs numpart 1) "0")
             "E" (- expval 1 (count leading-zeroes))))
      (let [[new-intpart extra-intpart] (if neg?
                                          [(subs intpart 0 2) (subs intpart 2)]
                                          [(subs intpart 0 1) (subs intpart 1)])
            new-exp (+ expval (count extra-intpart))]
        (str new-intpart
             "." (as-> (str extra-intpart decpart) $
                   (if (zero? (count $)) "0" $))
             "E" new-exp)))))

(defn- write-bigdec [^BigDecimal x ^Appendable out options]
  (let [bd (.stripTrailingZeros x)]
    ;; Check int length to protect against asymmetric attack
    ;; See https://github.com/simon-greatrix/CanonicalJson#security-addendum
    (if-let [bigint (when (> 30 (- (.scale bd)))
                      (try (.toBigIntegerExact bd)
                           (catch ArithmeticException _)))]
      (.append out (str bigint))
      (.append out (format-bigdec bd)))))

(defn- write-bigint [x ^Appendable out options]
  (write-bigdec (BigDecimal. (biginteger x)) out options))

(defn- write-float [^Float x ^Appendable out options]
  (cond (.isInfinite x)
        (throw (Exception. "JSON error: cannot write infinite Float"))
        (.isNaN x)
        (throw (Exception. "JSON error: cannot write Float NaN"))
        :else
        (->> (format "%.7E" x)
            BigDecimal. .stripTrailingZeros format-bigdec
            (.append out))))

(defn- write-double [^Double x ^Appendable out options]
  (cond (.isInfinite x)
        (throw (Exception. "JSON error: cannot write infinite Double"))
        (.isNaN x)
        (throw (Exception. "JSON error: cannot write Double NaN"))
        :else
        (->> (format "%.16E" x)
             BigDecimal. .stripTrailingZeros format-bigdec
             (.append out))))

(defn- write-plain [x ^Appendable out options]
  (.append out (str x)))

(defn- write-uuid [^java.util.UUID x ^Appendable out options]
  (.append out \")
  (.append out (.toString x))
  (.append out \"))

(defn- write-instant [^java.time.Instant x ^Appendable out options]
  (let [formatter ^java.time.format.DateTimeFormatter (:date-formatter options)]
    (.append out \")
    (.append out (.format formatter x))
    (.append out \")))

(defn- write-date [^java.util.Date x ^Appendable out options]
  (write-instant (.toInstant x) out options))

(defn- default-sql-date->instant-fn [^java.sql.Date d]
  (.toInstant (.atStartOfDay (.toLocalDate d) (java.time.ZoneId/systemDefault))))

(defn- write-sql-date [^java.sql.Date x ^Appendable out options]
  (let [->instant (:sql-date-converter options)]
    (write-instant (->instant x) out options)))

(defn- write-null [x ^Appendable out options]
  (.append out "null"))

(defn- write-named [x out options]
  (write-string (name x) out options))

(defn- write-generic [x out options]
  (if (.isArray (class x))
    (-write (seq x) out options)
    (throw (Exception. (str "Don't know how to write JSON of " (class x))))))

(defn- write-ratio [x out options]
  (-write (double x) out options))

;; nil, true, false
(extend nil                    JSONWriter {:-write write-null})
(extend java.lang.Boolean      JSONWriter {:-write write-plain})

;; Numbers
(extend java.lang.Byte         JSONWriter {:-write write-plain})
(extend java.lang.Short        JSONWriter {:-write write-plain})
(extend java.lang.Integer      JSONWriter {:-write write-plain})
(extend java.lang.Long         JSONWriter {:-write write-plain})
(extend java.lang.Float        JSONWriter {:-write write-float})
(extend java.lang.Double       JSONWriter {:-write write-double})
(extend clojure.lang.Ratio     JSONWriter {:-write write-ratio})
(extend java.math.BigInteger   JSONWriter {:-write write-bigint})
(extend java.math.BigDecimal   JSONWriter {:-write write-bigdec})
(extend java.util.concurrent.atomic.AtomicInteger JSONWriter {:-write write-plain})
(extend java.util.concurrent.atomic.AtomicLong    JSONWriter {:-write write-plain})
(extend java.util.UUID         JSONWriter {:-write write-uuid})
(extend java.time.Instant      JSONWriter {:-write write-instant})
(extend java.util.Date         JSONWriter {:-write write-date})
(extend java.sql.Date          JSONWriter {:-write write-sql-date})
(extend clojure.lang.BigInt    JSONWriter {:-write write-bigint})

;; Symbols, Keywords, and Strings
(extend clojure.lang.Named     JSONWriter {:-write write-named})
(extend java.lang.CharSequence JSONWriter {:-write write-string})

;; Collections
(extend java.util.Map          JSONWriter {:-write write-object})
(extend java.util.Collection   JSONWriter {:-write write-array})

;; Maybe a Java array, otherwise fail
(extend java.lang.Object       JSONWriter {:-write write-generic})

(def default-write-options {:sql-date-converter default-sql-date->instant-fn
                            :date-formatter java.time.format.DateTimeFormatter/ISO_INSTANT
                            :key-fn default-write-key-fn
                            :value-fn default-value-fn
                            :indent false
                            :indent-depth 0})
(defn write
  "Write JSON-formatted output to a java.io.Writer. Options are
   key-value pairs, valid options are:

    :sql-date-converter function

       Single-argument function used to convert a java.sql.Date to
       a java.time.Instant. As java.sql.Date does not have a
       time-component (which is required by java.time.Instant), it needs
       to be computed. The default implementation, `default-sql-date->instant-fn`
       uses
       ```
          (.toInstant (.atStartOfDay (.toLocalDate sql-date) (java.time.ZoneId/systemDefault)))
       ```

    :date-formatter

        A java.time.DateTimeFormatter instance, defaults to DateTimeFormatter/ISO_INSTANT

    :key-fn function

        Single-argument function called on map keys; return value will
        replace the property names in the output. Must return a
        string. Default calls clojure.core/name on symbols and
        keywords and clojure.core/str on everything else.

    :value-fn function

        Function to transform values in maps before writing. For each
        key-value pair in an input map, called with two arguments: the
        key (BEFORE transformation by key-fn) and the value. The
        return value of value-fn will replace the value in the output.
        If the return value is a number, boolean, string, or nil it
        will be included literally in the output. If the return value
        is a non-map collection, it will be processed recursively. If
        the return value is a map, it will be processed recursively,
        calling value-fn again on its key-value pairs. If value-fn
        returns itself, the key-value pair will be omitted from the
        output. This option does not apply to non-map collections."
  [x ^Writer writer & {:as options}]
  (-write x writer (merge default-write-options options)))

(defn write-str
  "Converts x to a JSON-formatted string. Options are the same as
  write."
  ^String [x & {:as options}]
  (let [sw (StringWriter.)]
    (-write x sw (merge default-write-options options))
    (.toString sw)))

;;; JSON PRETTY-PRINTER

;; Based on code by Tom Faulhaber

(defn- pprint-array [s] 
  ((pprint/formatter-out "~<[~;~@{~w~^, ~:_~}~;]~:>") s))

(defn- pprint-object [m options]
  (let [key-fn (:key-fn options)]
    ((pprint/formatter-out "~<{~;~@{~<~w:~_~w~:>~^, ~_~}~;}~:>")
     (for [[k v] m] [(key-fn k) v]))))

(defn- pprint-generic [x options]
  (if (.isArray (class x))
    (pprint-array (seq x))
    ;; pprint proxies Writer, so we can't just wrap it
    (print (with-out-str (-write x (PrintWriter. *out*) options)))))

(defn- pprint-dispatch [x options]
  (cond (nil? x) (print "null")
        (instance? java.util.Map x) (pprint-object x options)
        (instance? java.util.Collection x) (pprint-array x)
        (instance? clojure.lang.ISeq x) (pprint-array x)
        :else (pprint-generic x options)))

(defn pprint
  "Pretty-prints JSON representation of x to *out*. Options are the
  same as for write except :value-fn, which is not supported."
  [x & {:as options}]
  (let [opts (merge default-write-options options)]
    (pprint/with-pprint-dispatch #(pprint-dispatch % opts)
      (pprint/pprint x))))
