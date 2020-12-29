# Change Log

All notable changes to this project will be documented in this file. This change log follows the conventions of [keepachangelog.com](http://keepachangelog.com/).


## [0.1.3-SNAPSHOT] - _______

### Added
### Changed
### Removed


## [0.1.3-SNAPSHOT] - 2020-12-28

### Added
* Migrations, see `resources/migrations`
* HL7 FHIR R4 schema validation, see `resources/hl7-schemas`

### Changed
* Database configuration — see `.env.sample`; default use should be unaffected, but you'll need to create an `hl7` database using `createdb hl7` from the command line


## [0.1.3-SNAPSHOT] - 2020-12-15

### Changed
* Now just use `(start)` to start the server
* Expiry monitor:
    - can be stopped: `(pia-server.expiry-monitor/stop)`
    - interval can be changed by calling start with new interval: 
        `(pia-server.expiry-monitor/start 999)`
* Reduced transaction logging noise
