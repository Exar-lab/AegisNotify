# Changelog

## [0.8.0](https://github.com/Exar-lab/AegisNotify/compare/aegisnotify-v0.7.0...aegisnotify-v0.8.0) (2026-09-05)


### Features

* **design:** add OpenPencil dashboard mockups for notification monitoring UI ([8a53783](https://github.com/Exar-lab/AegisNotify/commit/8a53783856117ad5440132a640cde0ff7aea1910))
* **design:** add OpenPencil dashboard mockups for notification monitoring UI ([8dfad35](https://github.com/Exar-lab/AegisNotify/commit/8dfad355246c64d38387a8bebfd03de71d9f2fb1)), closes [#75](https://github.com/Exar-lab/AegisNotify/issues/75)
* **docker:** add Keycloak service and realm export for local auth ([c8ac252](https://github.com/Exar-lab/AegisNotify/commit/c8ac252ff0235fc36b12b44cd60fa12e50eaf1a3))
* **notification:** activate the outbox relay with a scheduled worker ([4ece5bf](https://github.com/Exar-lab/AegisNotify/commit/4ece5bf983eeef4d17889545fa6e13354855e134))
* **notification:** add aggregation buffer with individual-delivery fallback ([bf0d701](https://github.com/Exar-lab/AegisNotify/commit/bf0d7015404b23dd7f08f3c1193fbac15aa364a1))
* **notification:** add audit fan-out and finish D13 exclusion config ([71942f2](https://github.com/Exar-lab/AegisNotify/commit/71942f2218171db3bcaff6284479a7ac8a3e5db9))
* **notification:** add Kafka consumer for priority topics ([#59](https://github.com/Exar-lab/AegisNotify/issues/59)) ([271be9f](https://github.com/Exar-lab/AegisNotify/commit/271be9f83360794789dd005c87cdac42251c26cb))
* **notification:** add kafka consumer metrics ([3ccb8d8](https://github.com/Exar-lab/AegisNotify/commit/3ccb8d85b986e6302dabb1629897b6d9b19b736a))
* **notification:** add LLM summarizer for aggregated notifications ([feb2568](https://github.com/Exar-lab/AegisNotify/commit/feb2568c135b73092a23fa539fc733e2aacd111f))
* **notification:** add Micrometer custom metrics for requests, errors, latency, fallback ([#67](https://github.com/Exar-lab/AegisNotify/issues/67)) ([0c169d3](https://github.com/Exar-lab/AegisNotify/commit/0c169d36aed7e7d64756bdbde3ac833f01150184))
* **notification:** add Resilience4j circuit breaker with provider failover ([#62](https://github.com/Exar-lab/AegisNotify/issues/62)) ([d7a3d31](https://github.com/Exar-lab/AegisNotify/commit/d7a3d31a505538b99c292185d8734115c1ac092c))
* **notification:** add typed Kafka runtime properties ([28510ed](https://github.com/Exar-lab/AegisNotify/commit/28510edaea6b0087cc5671c152000bda4b1ed0a0))
* **notification:** add typed Kafka runtime properties ([36c9478](https://github.com/Exar-lab/AegisNotify/commit/36c9478deebfc33c9fc925a779d77d2feb1ded5f))
* **notification:** AI-powered notification aggregation ([50367f9](https://github.com/Exar-lab/AegisNotify/commit/50367f9f86238f37c3550afefbfb372e58ca2c5e))
* **notification:** classify provider failures as retryable by HTTP status ([9dbe1d6](https://github.com/Exar-lab/AegisNotify/commit/9dbe1d6fa2ce516bf178ec578f6481177b606f15))
* **notification:** classify provider failures as retryable by HTTP status ([ce9fc8a](https://github.com/Exar-lab/AegisNotify/commit/ce9fc8ae5c84c59d3676791ea5b34999b7bd5859))
* **notification:** classify provider failures as retryable by HTTP status ([#95](https://github.com/Exar-lab/AegisNotify/issues/95)) ([9dbe1d6](https://github.com/Exar-lab/AegisNotify/commit/9dbe1d6fa2ce516bf178ec578f6481177b606f15))
* **notification:** compose Resilience4j Retry around the circuit breaker ([d749c9c](https://github.com/Exar-lab/AegisNotify/commit/d749c9c11630458f6fd3eddb185adb5755a05e5d))
* **notification:** compose Resilience4j Retry around the circuit breaker ([504c972](https://github.com/Exar-lab/AegisNotify/commit/504c972c2fadd4ab4f26b6cb0d2da977a8fceb0f))
* **notification:** compose Resilience4j Retry around the circuit breaker ([#96](https://github.com/Exar-lab/AegisNotify/issues/96)) ([d749c9c](https://github.com/Exar-lab/AegisNotify/commit/d749c9c11630458f6fd3eddb185adb5755a05e5d))
* **notification:** deliver reliable Kafka notification pipeline ([24e3c19](https://github.com/Exar-lab/AegisNotify/commit/24e3c1972bb71c7cac72c791b7f25ebf00f9e9d2))
* **notification:** harden template rendering and boot with real bean ([e2def80](https://github.com/Exar-lab/AegisNotify/commit/e2def8026258bf4eeafbaa93eed48466c37a580c))
* **notification:** harden template rendering and boot with real bean ([35aca87](https://github.com/Exar-lab/AegisNotify/commit/35aca874b4ef4581b675e6bb65d8cf1f911c86a0))
* **notification:** implement Kafka broker adapter and activate outbox relay ([a5c80f0](https://github.com/Exar-lab/AegisNotify/commit/a5c80f0b06d2c112b63ceec59a0fcff599521ca2))
* **notification:** implement Kafka message broker adapter ([fbb2906](https://github.com/Exar-lab/AegisNotify/commit/fbb2906f76144c3d96bd01dbe2f398facef75f64))
* **notification:** implement TemplateRenderer with Mustache ([da9eca3](https://github.com/Exar-lab/AegisNotify/commit/da9eca337d11286fab3edfc8be2082687b70ef78))
* **notification:** implement TemplateRenderer with Mustache ([ed254ca](https://github.com/Exar-lab/AegisNotify/commit/ed254ca08916b08391d57af1e83455aaea0ed762))
* **security:** enforce notification:read and audit:read scopes ([b2af6e8](https://github.com/Exar-lab/AegisNotify/commit/b2af6e881d03388842e3bf5f55a509c66aeaa1d1))
* **security:** enforce notification:read and audit:read scopes ([be84e9d](https://github.com/Exar-lab/AegisNotify/commit/be84e9d829a1a29c49c33e4b68b7396db3dd5cd9))
* **security:** enforce per-route scope authorization at the gateway ([d5c6ec0](https://github.com/Exar-lab/AegisNotify/commit/d5c6ec00656655efdd095f89281eafd129df736d))
* **security:** Keycloak integration and scope-based authorization ([9e95758](https://github.com/Exar-lab/AegisNotify/commit/9e95758962258d615bc486424e2fd1740e928697))
* **user-service:** add disable-only user mutation endpoints ([2a5a250](https://github.com/Exar-lab/AegisNotify/commit/2a5a2508d361ea7d087f52aa11b1a0cf3b8733c6))
* **user-service:** add Keycloak-backed user administration ([dbe8b05](https://github.com/Exar-lab/AegisNotify/commit/dbe8b055a6f6ef48006cfd58994915e89048a064))
* **user-service:** add Keycloak-backed user read path ([68c087c](https://github.com/Exar-lab/AegisNotify/commit/68c087cc2654d4c5d03b2b3c0642a35ad471c4b1))
* **user-service:** scaffold aegis-user-service module ([5dda8db](https://github.com/Exar-lab/AegisNotify/commit/5dda8dbaa398d6799f243d99584aa5a0faa0db65))


### Bug Fixes

* **ci:** remove show_full_output left enabled on main after CI debugging ([#58](https://github.com/Exar-lab/AegisNotify/issues/58)) ([6656a47](https://github.com/Exar-lab/AegisNotify/commit/6656a472f251a983897c8b9edf3f4f6eccf01d06))
* **ci:** restore Claude pull request reviews ([849e018](https://github.com/Exar-lab/AegisNotify/commit/849e018a0de5fa843c59685f8e80ba61e80ad97a))
* **ci:** restore Claude pull request reviews ([57ca275](https://github.com/Exar-lab/AegisNotify/commit/57ca275d3b2424b219f8587b717d324f67d0a823))
* **docker:** add required firstName/lastName to aegis-dev test user ([b7353fc](https://github.com/Exar-lab/AegisNotify/commit/b7353fc3d6c200ce4cf4cf8c449cc54d17c51b68))
* **notification:** address Copilot review feedback on PR [#93](https://github.com/Exar-lab/AegisNotify/issues/93) ([631bb66](https://github.com/Exar-lab/AegisNotify/commit/631bb668b121bd61727de7e1d143dc90b27597d6))
* **notification:** clear persistence context after aggregation_buffer bulk updates ([a04463e](https://github.com/Exar-lab/AegisNotify/commit/a04463ee40b748ccdaf7837aeb5e366a8779e6bc))
* **notification:** fix real-Docker CI failures surfaced by PR [#94](https://github.com/Exar-lab/AegisNotify/issues/94) ([efdaf9b](https://github.com/Exar-lab/AegisNotify/commit/efdaf9b7332ea15986fa9436a7f050ad7a6fc178))
* **notification:** fix remaining real-Docker CI failures on PR [#94](https://github.com/Exar-lab/AegisNotify/issues/94) ([b3e0323](https://github.com/Exar-lab/AegisNotify/commit/b3e032315bf2ea336031a70af8a003dd7fa85947))
* **notification:** flush jsonb-typed entity writes immediately to stop lost aggregate outbox events ([e0897e8](https://github.com/Exar-lab/AegisNotify/commit/e0897e81ce63f02ac976cc235d156ead6b13d0bb))
* **notification:** mock DeadLetterQueuePort in AggregationBufferRepositoryAdapterIntegrationTest ([7a6e366](https://github.com/Exar-lab/AegisNotify/commit/7a6e3668ec1a579613ea261fc8f6d1856da1148d))
* **notification:** mock DeadLetterQueuePort in FlushAggregationWindowsIntegrationTest ([f148711](https://github.com/Exar-lab/AegisNotify/commit/f148711111d388465af7806bbcf52286a3d1501c))
* **notification:** mock DeadLetterQueuePort in OutboxWorkerSchedulerIntegrationTest ([07b60e5](https://github.com/Exar-lab/AegisNotify/commit/07b60e55a190fe86d783f781f36337c22f758633))
* **notification:** mock TaskScheduler in FlushAggregationWindowsIntegrationTest ([1f48609](https://github.com/Exar-lab/AegisNotify/commit/1f486091803ea9be7db802fd65ed9b622a68de55))
* **notification:** satisfy checkstyle for temp diagnostic test change ([b4c2285](https://github.com/Exar-lab/AegisNotify/commit/b4c2285fb2f6c6e8364e04796071f9bb00ae2191))
* **notification:** stop blocking provider HTTP calls inside a DB transaction ([eea69f8](https://github.com/Exar-lab/AegisNotify/commit/eea69f85226656393741df75ecad4c232897ba1c))
* **notification:** stop blocking provider HTTP calls inside a DB transaction ([bc6b230](https://github.com/Exar-lab/AegisNotify/commit/bc6b23039a475c8528524d86ef2f22e12c5ca7c7))
* **notification:** update Kafka consumer test mock to widened TemplateRenderer ([536473a](https://github.com/Exar-lab/AegisNotify/commit/536473a608ce9e15997111cba9f73ccd21492208))


### Documentation

* add installation guide, project scope, and CONTRIBUTING ([3b92bac](https://github.com/Exar-lab/AegisNotify/commit/3b92bacdbcc209cdd31368c42b4c9324513e45fb))
* add installation guide, project scope, and CONTRIBUTING ([9cf9ba0](https://github.com/Exar-lab/AegisNotify/commit/9cf9ba0e6e08546e06514ecb91356f59d1bbc3ba))
* address Copilot review feedback on PR [#89](https://github.com/Exar-lab/AegisNotify/issues/89) ([b38936f](https://github.com/Exar-lab/AegisNotify/commit/b38936fcd886fd28546b65d31265dfd1c57b62e7))
* **readme:** add visual architecture diagrams ([#66](https://github.com/Exar-lab/AegisNotify/issues/66)) ([b587efe](https://github.com/Exar-lab/AegisNotify/commit/b587efe1b25ed4878f54d0822c56990315658174))
* **readme:** document local Keycloak JWT retrieval for issue [#74](https://github.com/Exar-lab/AegisNotify/issues/74) ([03697ba](https://github.com/Exar-lab/AegisNotify/commit/03697ba61173353bb3e3171dc6d19c6eec709553))
* **readme:** document project architecture and capabilities ([#64](https://github.com/Exar-lab/AegisNotify/issues/64)) ([bd3389c](https://github.com/Exar-lab/AegisNotify/commit/bd3389cdef8a4686cae51fc6bebd8c99fa891a54))


### Tests

* **notification:** add same-tx readback diagnostic ([d0304c5](https://github.com/Exar-lab/AegisNotify/commit/d0304c5cd1e698ce6d870bfdd650cdd60e1c0690))
* **notification:** add temp diagnostic dump to flaky aggregation flush test ([7696d42](https://github.com/Exar-lab/AegisNotify/commit/7696d42d07af4dc619133ab3f9e5676bad661471))
* **notification:** add temp diagnostic tracing to flushGroup/flushAggregate ([b26eaa8](https://github.com/Exar-lab/AegisNotify/commit/b26eaa8bf18134257ea48f188bf36180a991d67d))
* **notification:** force explicit entityManager.flush() to surface swallowed errors ([b09d207](https://github.com/Exar-lab/AegisNotify/commit/b09d20727c15106f55d8970cd39098cdd185392a))
* **notification:** log rollbackOnly flag at end of flushAggregate ([30c9e2a](https://github.com/Exar-lab/AegisNotify/commit/30c9e2a2d27549b0ecbf0aa42d987ed6b2b5f3ca))
* **notification:** log transaction active/readonly state in flushAggregate ([b952ae5](https://github.com/Exar-lab/AegisNotify/commit/b952ae5eb396893068925538a636faa877d14e3e))
* **notification:** verify retry metrics and YAML-bound retry predicate ([34c2890](https://github.com/Exar-lab/AegisNotify/commit/34c28906250aaceceab42bbd27d6d8b02c86a526))
* **notification:** verify retry metrics and YAML-bound retry predicate ([204d8cc](https://github.com/Exar-lab/AegisNotify/commit/204d8cc8985424ac81c10bf654bb42e7316e1cfa))
* **notification:** verify retry metrics and YAML-bound retry predicate ([#97](https://github.com/Exar-lab/AegisNotify/issues/97)) ([34c2890](https://github.com/Exar-lab/AegisNotify/commit/34c28906250aaceceab42bbd27d6d8b02c86a526))


### CI/CD

* **fix:** pin claude-code-action to last known-good commit ([#61](https://github.com/Exar-lab/AegisNotify/issues/61)) ([f54b3c4](https://github.com/Exar-lab/AegisNotify/commit/f54b3c419161541237f6c39972452ce5e72c91d2))
* re-enable show_full_output to capture claude-review error ([3babf74](https://github.com/Exar-lab/AegisNotify/commit/3babf749215d934a6911ec13df6121040a1a4d73))
* remove temporary show_full_output debug flag ([43eb0b9](https://github.com/Exar-lab/AegisNotify/commit/43eb0b9e811931fae04854d0cdddbfb9f7d37691))
* retrigger claude-review check ([6fa7616](https://github.com/Exar-lab/AegisNotify/commit/6fa76164af5ebc2ec2cda5d9db694a6bea378c08))
* temporarily enable show_full_output to debug claude-review failure ([2d6c488](https://github.com/Exar-lab/AegisNotify/commit/2d6c488c8e1b46c1cf63315832bfaec7086fcef7))

## [0.7.0](https://github.com/Exar-lab/AegisNotify/compare/aegisnotify-v0.6.0...aegisnotify-v0.7.0) (2026-07-10)


### Features

* **audit:** add SecurityConfig to protect audit endpoints ([2b13660](https://github.com/Exar-lab/AegisNotify/commit/2b136603f81642b995ea5cf296ef3d7f129a0b34))
* **notification:** implement notification provider adapters ([b3a4f25](https://github.com/Exar-lab/AegisNotify/commit/b3a4f25ee1e2adb809a616ac972c3dabb0be268d))
* **notification:** implement notification provider adapters ([fc62fd4](https://github.com/Exar-lab/AegisNotify/commit/fc62fd4046776d0e39e41f97e66a0f9bb3aa4d25))


### Bug Fixes

* **audit:** use dummy jwk-set-uri in test config ([8755b29](https://github.com/Exar-lab/AegisNotify/commit/8755b2963db0802dcd6362bee8acffdd5744dfc8))

## [0.6.0](https://github.com/Exar-lab/AegisNotify/compare/aegisnotify-v0.5.1...aegisnotify-v0.6.0) (2026-07-07)


### Features

* **notification:** add SecurityConfig with OAuth2 Resource Server JWT validation ([e5c6be1](https://github.com/Exar-lab/AegisNotify/commit/e5c6be185a83a60e77efc839dc3132cdda064dd4))


### Bug Fixes

* **notification:** address Copilot review on SecurityConfig ([a3fd592](https://github.com/Exar-lab/AegisNotify/commit/a3fd592a086f4c01d81e5e5bfa4c5af06f4f1e69))

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
