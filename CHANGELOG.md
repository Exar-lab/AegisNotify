# Changelog

## [0.5.1](https://github.com/Exar-lab/AegisNotify/compare/aegisnotify-v0.5.0...aegisnotify-v0.5.1) (2026-07-07)


### Bug Fixes

* **notification:** add CANCELLED to notification_logs status constraint ([b1d5214](https://github.com/Exar-lab/AegisNotify/commit/b1d52146880adec6cd9ec810bbb9fb9665f225b6))
* **notification:** add CANCELLED to notification_logs status constraint ([ccf70ce](https://github.com/Exar-lab/AegisNotify/commit/ccf70ceb9fa6d14c53fcaf088bed92c445448cf3))

## [0.5.0](https://github.com/Exar-lab/AegisNotify/compare/aegisnotify-v0.4.0...aegisnotify-v0.5.0) (2026-07-04)


### Features

* **audit:** add audit-service infrastructure layer ([fa42878](https://github.com/Exar-lab/AegisNotify/commit/fa42878fab5a288627722b90fb8b1abaf7497115))
* **audit:** add cross-module verification ([605f117](https://github.com/Exar-lab/AegisNotify/commit/605f1170431a290def4ed7bc5b616667080bb965))
* **gateway:** add aegis-api-gateway module with centralized JWT validation ([5b3e066](https://github.com/Exar-lab/AegisNotify/commit/5b3e066da0fc0cdddc7f5bb5f6504ea0f36e701d))
* **gateway:** add aegis-api-gateway module with JWT validation and routing ([74c1339](https://github.com/Exar-lab/AegisNotify/commit/74c13399ac78dc9d8dbbb2d0f6291029ca0fb668))
* **notification:** add audit event publishing via Kafka ([758afd4](https://github.com/Exar-lab/AegisNotify/commit/758afd46eebb8dcbe1d485c30ce872a0c2846308))


### Bug Fixes

* **gateway:** add explicit relativePath to parent POM for CI resolution ([4b78069](https://github.com/Exar-lab/AegisNotify/commit/4b780692a0bd6fed6fd16efd144d9569a3d604d7))


### CI/CD

* use validate lifecycle phase for checkstyle to fix reactor parent resolution ([a94816b](https://github.com/Exar-lab/AegisNotify/commit/a94816b406977cc6457dc40c7c8e79275fd878b1))

## [0.4.0](https://github.com/Exar-lab/AegisNotify/compare/aegisnotify-v0.3.0...aegisnotify-v0.4.0) (2026-06-22)


### Features

* **audit:** add aegis-audit-service module — domain and application layer ([26583aa](https://github.com/Exar-lab/AegisNotify/commit/26583aaff13362a643bc72400209ddfc69b5fc16))
* **audit:** add aegis-audit-service module with domain and application layer ([47dfd2b](https://github.com/Exar-lab/AegisNotify/commit/47dfd2bdcaddd90ccab93fb5e9224f827de1954b))


### Bug Fixes

* **audit:** align parent version with main (0.3.1-SNAPSHOT) ([7504c25](https://github.com/Exar-lab/AegisNotify/commit/7504c250aa9e9dd85789ce55cfba61d816257b23))

## [0.3.0](https://github.com/Exar-lab/AegisNotify/compare/aegisnotify-v0.2.1...aegisnotify-v0.3.0) (2026-06-22)


### Features

* **notification:** add 9 application use cases ([010ccb3](https://github.com/Exar-lab/AegisNotify/commit/010ccb33ba704378b6d555dfcac5b0e7920826a6))
* **notification:** add 9 application use cases with ports and domain transitions ([5296ef4](https://github.com/Exar-lab/AegisNotify/commit/5296ef4263ca9f677e253f572639707c2d125ced))


### Tests

* **notification:** mock pending outbound ports in context test ([cbaff1d](https://github.com/Exar-lab/AegisNotify/commit/cbaff1d6e98e41fa959604560d9ff3d68af3fab4))

## [0.2.1](https://github.com/Exar-lab/AegisNotify/compare/aegisnotify-v0.2.0...aegisnotify-v0.2.1) (2026-06-21)


### Bug Fixes

* **notification:** restore default branch in switch for checkstyle compliance ([d72e085](https://github.com/Exar-lab/AegisNotify/commit/d72e085c59db32fcf079be90a674cbda12dd2b20))


### Documentation

* add README for project and modules ([2bf5eda](https://github.com/Exar-lab/AegisNotify/commit/2bf5edaea7e10af6ba9e31d732acdebda17f872e))
* add README for project root and each module ([725126c](https://github.com/Exar-lab/AegisNotify/commit/725126c362310e0d8f768078fc50cfe6b664908d))

## [0.2.0](https://github.com/Exar-lab/AegisNotify/compare/aegisnotify-v0.1.0...aegisnotify-v0.2.0) (2026-06-21)


### Features

* add notification service module with Ingress API ([44b2d21](https://github.com/Exar-lab/AegisNotify/commit/44b2d21629337e6ed669cb1f2c40a8af969b8844))
* add notification service module with Ingress API ([26fe95c](https://github.com/Exar-lab/AegisNotify/commit/26fe95c6733a0bc834c387eb35ca80185afe60d9))
* add Spring Cloud Config Server module ([bab3484](https://github.com/Exar-lab/AegisNotify/commit/bab348404258c4a37ceb238456b81c9088d0df0a))
* add Spring Cloud Config Server module ([982978d](https://github.com/Exar-lab/AegisNotify/commit/982978db9d5d6eae03b641cef13187edb6ecbd4f))


### Bug Fixes

* **config-server:** align parent version to 0.1.1-SNAPSHOT ([34b4277](https://github.com/Exar-lab/AegisNotify/commit/34b4277124026a5cdb3cbfe119021d460d94ea0e))
* **eureka:** fix checkstyle indentation violations ([a3896f0](https://github.com/Exar-lab/AegisNotify/commit/a3896f0260c98be1871ab9e123d349d4fc462b4a))
* resolve merge conflict with config-server module in reactor ([46f1987](https://github.com/Exar-lab/AegisNotify/commit/46f19873ca41f0f7f9f8522c8afe72e3f1ffac95))

## [0.1.0](https://github.com/Exar-lab/AegisNotify/compare/aegisnotify-v0.0.1...aegisnotify-v0.1.0) (2026-06-20)


### Features

* initial project setup with Eureka Server and database schema ([b877633](https://github.com/Exar-lab/AegisNotify/commit/b877633e267a203792251aed45d8319bb5ecf6a9))


### Bug Fixes

* **ci:** fix mvnw permission and add checkstyle to pipeline ([65db55b](https://github.com/Exar-lab/AegisNotify/commit/65db55babde9caded03bc7e1f059ae50eb96b8b7))


### CI/CD

* **gga:** configure Guardian Angel for Java file patterns ([113f65c](https://github.com/Exar-lab/AegisNotify/commit/113f65cc2e577bad1b19e06f1c1b4e55d8a337a0))
