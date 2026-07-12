# Changelog

## [0.7.0](https://github.com/PTLRepoHub/addressiq-android/compare/v0.6.0...v0.7.0) (2026-07-12)


### ⚠ BREAKING CHANGES

* `AddressIQEnvironment.SANDBOX` is renamed to `STAGING`. A deprecated companion `@JvmField val SANDBOX` keeps references compiling, but Kotlin enums cannot carry a deprecated alias *entry* — so a Java `switch (env) { case SANDBOX: }` will NO LONGER COMPILE. Kotlin and Swift callers are unaffected.

### Features

* per-environment build config and CDN widget loading ([#17](https://github.com/PTLRepoHub/addressiq-android/issues/17)) ([1da1f20](https://github.com/PTLRepoHub/addressiq-android/commit/1da1f20dd7a77571d7d13ebef466ebf8692c7566))
* **widget:** re-vendor iqcollect.js from web v0.5.1 ([#20](https://github.com/PTLRepoHub/addressiq-android/issues/20)) ([9f0abe8](https://github.com/PTLRepoHub/addressiq-android/commit/9f0abe804a5d6455c9cbed088af7db9999c3950d))

## [0.6.0](https://github.com/PTLRepoHub/addressiq-android/compare/v0.5.0...v0.6.0) (2026-07-12)


### Features

* **widget:** re-vendor iqcollect.js from web v0.4.2 ([#15](https://github.com/PTLRepoHub/addressiq-android/issues/15)) ([426f31f](https://github.com/PTLRepoHub/addressiq-android/commit/426f31f3b14c41409cc54ea356b38159f8be47ff))

## [0.5.0](https://github.com/PTLRepoHub/addressiq-android/compare/v0.4.0...v0.5.0) (2026-07-12)


### Features

* implement Android transit-event telemetry ingestion ([#11](https://github.com/PTLRepoHub/addressiq-android/issues/11)) ([485b4f3](https://github.com/PTLRepoHub/addressiq-android/commit/485b4f37318d35483af08703cb4576d4aa7fcfc4))
* **widget:** re-vendor iqcollect.js from web v0.4.0 ([#10](https://github.com/PTLRepoHub/addressiq-android/issues/10)) ([af6490b](https://github.com/PTLRepoHub/addressiq-android/commit/af6490bb7ec53332532fdd43c577e5ca0888f609))
* **widget:** re-vendor iqcollect.js from web v0.4.1 ([#14](https://github.com/PTLRepoHub/addressiq-android/issues/14)) ([cf92443](https://github.com/PTLRepoHub/addressiq-android/commit/cf92443a63d3ab4e19571688c5360eb6c86b3bd5))


### Bug Fixes

* make SDK API-safe on minSdk 24 and gate lint in CI ([#13](https://github.com/PTLRepoHub/addressiq-android/issues/13)) ([982ef1a](https://github.com/PTLRepoHub/addressiq-android/commit/982ef1abac5b7d5be2b36cf67cef9a4feb8906c8))

## [0.4.0](https://github.com/PTLRepoHub/addressiq-android/compare/v0.3.0...v0.4.0) (2026-07-12)


### ⚠ BREAKING CHANGES

* removed AddressIQConfig.apiUrl and AddressIQVerifyInput apiUrlOverride. Select a host via `environment` (PRODUCTION | SANDBOX | DEVELOPMENT); production is provisioned at build time.

### Features

* provision API URL (+ web: Maps key) at build time from GH env ([#8](https://github.com/PTLRepoHub/addressiq-android/issues/8)) ([32efe56](https://github.com/PTLRepoHub/addressiq-android/commit/32efe562c277c58157eb1d8003dd1483582ab7df))

## [0.3.0](https://github.com/PTLRepoHub/addressiq-android/compare/v0.2.0...v0.3.0) (2026-07-12)


### ⚠ BREAKING CHANGES

* removed AddressIQVerifyInput.googleMapsApiKey. The key is provisioned automatically by the platform; there is nothing to pass.

### Features

* provision Google Maps key automatically; remove googleMapsApiKey ([#6](https://github.com/PTLRepoHub/addressiq-android/issues/6)) ([9ca2318](https://github.com/PTLRepoHub/addressiq-android/commit/9ca2318360553603363fa2fe3e3a1917e69490c4))

## [0.2.0](https://github.com/PTLRepoHub/addressiq-android/compare/v0.1.0...v0.2.0) (2026-07-10)


### Features

* **proto:** regen against proto v0.1.0 ([#4](https://github.com/PTLRepoHub/addressiq-android/issues/4)) ([4d9c430](https://github.com/PTLRepoHub/addressiq-android/commit/4d9c43084bb778af26069d9da1912562d9f01de5))

## 0.1.0 (2026-07-10)


### ⚠ BREAKING CHANGES

* a missing widget bundle now yields AddressIQVerifyResult.Failed instead of silently fetching from a CDN. The widgetUrl input still works as a dev override.

### Features

* AddressIQ Android SDK + example + CI/CD ([a7a7658](https://github.com/PTLRepoHub/addressiq-android/commit/a7a765823218f829610fdc17bdb5de4d855abf80))
* **android:** collect→verify split + sample demoing all 3 verification types ([92251ef](https://github.com/PTLRepoHub/addressiq-android/commit/92251efdf305f26bbad8355daf8d85d2b37b395a))
* **android:** maps address-capture + Street View refinements in Collect UI ([55f49f9](https://github.com/PTLRepoHub/addressiq-android/commit/55f49f94ece578c6215c17d1fc6356d78fc85342))
* **examples:** split into examples/kotlin + examples/java ([37db46f](https://github.com/PTLRepoHub/addressiq-android/commit/37db46fa3c8ec2f3ea932a663e772faa8a0535d9))
* fail closed when the bundled widget is missing ([54cf37c](https://github.com/PTLRepoHub/addressiq-android/commit/54cf37c636e4e256f24cf360c88c34eacf6900bc))
* **proto:** generate wire-contract bindings from AddressIq-proto ([99fddce](https://github.com/PTLRepoHub/addressiq-android/commit/99fddce75be55442c01983527bde1c6efa56461a))


### Bug Fixes

* **ci:** set bump-minor-pre-major in release-please config ([dae2979](https://github.com/PTLRepoHub/addressiq-android/commit/dae2979e04fd9ab9c906b2ac050a40a774af254e))
* **example:** add Compose compiler plugin (Kotlin 2.0) + compileSdk 36 ([bacd92f](https://github.com/PTLRepoHub/addressiq-android/commit/bacd92f8be15af01ab3ebaf80dc3879ca70cb561))
* **example:** use startPhysicalVerification + add lifecycle-viewmodel-compose ([ef2cacd](https://github.com/PTLRepoHub/addressiq-android/commit/ef2cacdf3accea74428a5becf123619d622715a7))
* **publish:** migrate to Central Portal and correct the coordinates ([ee007a7](https://github.com/PTLRepoHub/addressiq-android/commit/ee007a7e34657b5ab668df90f08d3aa1f524af9a))
* re-vendor iqcollect.js with addressiqpro.com URLs ([38c9966](https://github.com/PTLRepoHub/addressiq-android/commit/38c9966dae32d867dfb610fbf9e8ba28b8e50582))
* **sdk:** add Material Components dep for the XML Theme.Material3 parent ([61e0681](https://github.com/PTLRepoHub/addressiq-android/commit/61e0681f8c71e964fbd59ed5bff3e2ce618eb87a))
* **theme,examples:** honour partner theming + full Java parity example ([058bd16](https://github.com/PTLRepoHub/addressiq-android/commit/058bd16d302d9bd369b27ad6f387d730818d4e22))


### Miscellaneous Chores

* cut the first release as 0.1.0 ([915dc01](https://github.com/PTLRepoHub/addressiq-android/commit/915dc014946367aecc250e6568bf158f2fc1ef11))
