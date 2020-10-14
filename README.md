# pia-server

FIXME

## Usage

### Getting Started

* install Clojure
```shell
brew install clojure/tools/clojure
```

* install Leiningen
https://leiningen.org/#install

* clone this repo
```shell
git clone git+ssh://git@github.com/precisely/pia-server.git```
```
* configure secrets:
  - copy env.edn.sample to env.edn, update any auth tokens & secrets
  - env.edn is in .gitignore

### IntelliJ tips:

* Install Cursive
* Create Configuration "Clojure REPL" (if it doesn't exist) 
   - Type: nREPL, How to Run it: Run with Leiningen
* Create a REPL command: Tools > REPL > Add New REPL Command
  - Name it "Run All Tests" and add the following code
```clojure
(letfn [(list-sources [test-dir]
          (map #(clojure.string/replace (second (re-matches (re-pattern (str test-dir "/(.*)\\.clj?$")) (.getPath %))) "/" ".")
               (filter #(.isFile %) (file-seq (java.io.File. test-dir)))))]

  (run! #(require (symbol %))
        (list-sources "test"))
    (clojure.test/run-all-tests #"pia-server.*test.*"))
```
### Run the application locally

`lein ring server`

### Run the tests

`lein test`

### Packaging and running as standalone jar

```
lein do clean, ring uberjar
java -jar target/server.jar
```

### Packaging as war

`lein ring uberwar`

## License

Copyright ©  FIXME
