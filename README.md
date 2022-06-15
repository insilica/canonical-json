canonical-json
========================================

[canonicaljson-spec](https://insilica.github.io/canonicaljson-spec/)-compliant JSON parser/generator. Forked from [clojure.data.json](https://github.com/clojure/data.json).

Key goals:
* Compliant with JSON spec per https://json.org/
* Compliant with [canonicaljson-spec](https://insilica.github.io/canonicaljson-spec/)
  * canonicaljson-spec was selected over the alternatives because it works for all valid JSON data
* No external dependencies
* Compatibility with [babashka](https://babashka.org/)


Install
----------------------------------------

[CLI/`deps.edn`](https://clojure.org/reference/deps_and_cli) dependency information:
```clojure
co.insilica/canonical-json {:git/sha "f396256fa81beef63682f85dc72e9a8be6fe9bfb"
                            :git/url "https://github.com/insilica/canonical-json"}
```


Usage
----------------------------------------

The API is similar to clojure.data.json's, but with less options.
See the [clojure.data.json API Documentation](https://clojure.github.io/data.json/)


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
