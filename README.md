canonical-json
========================================

[![Clojars Project](https://img.shields.io/clojars/v/co.insilica/canonical-json.svg)](https://clojars.org/co.insilica/canonical-json)


[canonicaljson-spec](https://insilica.github.io/canonicaljson-spec/)-compliant JSON parser/generator. Forked from [clojure.data.json](https://github.com/clojure/data.json).

Key goals:
* Compliant with JSON spec per https://json.org/
* Compliant with [canonicaljson-spec](https://insilica.github.io/canonicaljson-spec/)
  * canonicaljson-spec was selected over the alternatives because it works for all valid JSON data
* No external dependencies
* Compatibility with [babashka](https://babashka.org/)

Usage
----------------------------------------

The API is similar to clojure.data.json's, but with less options.
See the [clojure.data.json API Documentation](https://clojure.github.io/data.json/). The `:bigdec` option has been removed from the read functions. The
`:escape-js-separators`, `:escape-slash`, and `:escape-unicode` options have been
removed from the write functions.

When reading in JSON data to canonicalize it, it is recommended to use the read functions from canonical-json rather than another JSON library. canonical-json uses BigDecimals to represent JSON numbers, which avoids a lot of subtle inconsistencies when round-tripping data.

Test
----------------------------------------

Run project tests:
```
lein test
```

Test against canonicaljson-spec:
```
git submodule init
git submodule update
./test-spec.sh
```


Change Log
----------------------------------------

* Release [3.0.0] on 2022-Jun-14
  * Fork from [clojure.data.json](https://github.com/clojure/data.json)
  * Implement canonicaljson-spec output and round-tripping

[3.0.0]: https://github.com/insilica/canonical-json/tree/v3.0.0

Copyright and License
----------------------------------------

Copyright (c) Stuart Sierra, Rich Hickey, and contributors 2012-2022.
All rights reserved.  The use and
distribution terms for this software are covered by the Eclipse Public
License 1.0 (https://opensource.org/licenses/eclipse-1.0.php) which can
be found in the file epl-v10.html at the root of this distribution.
By using this software in any fashion, you are agreeing to be bound by
the terms of this license.  You must not remove this notice, or any
other, from this software.
