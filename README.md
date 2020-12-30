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
  - copy `.env.sample` to `.env`, update any auth tokens & secrets
  - NOTE: the pia-developer user in our AWS dev-precisely account has minimal permissions for retrieving the longterm library from our S3 bucket    
  - env.edn is in .gitignore


### Database setup

#### Install Postgres

```shell
brew install postgres
```

#### Create dbs

```shell
createdb pia_runstore
createdb test_pia_runstore
createdb hl7
```

#### Migrations

Migrations should run automatically on PIA server startup, but can also manually run like this:
```shell
lein run -m scripts/create-db
```

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

### Start the app 

#### REPL mode

Recommended for development. Allows hot reloading code with your development environment.

```clojure
(start)
```

#### Command line mode

With this approach, you will need to stop and restart the server if you change any files in pia-server, but you avoid having to start a REPL. This might be useful if you want to start pia-server from a script.

```bash
lein ring server
```

### Run the tests

`lein test`

### Packaging and running as standalone jar

```
lein do clean, ring uberjar
java -jar target/server.jar
```

### Packaging as war

`lein ring uberwar`
