# Code Review Rules

## Java
- Java 21, use modern language features (records, sealed classes, pattern matching)
- Follow Google Java Style Guide (enforced by Checkstyle)
- No wildcard imports
- Prefer immutable objects and final fields

## Architecture (Hexagonal / Clean)
- Domain layer must not depend on application or infrastructure
- Application layer must not depend on infrastructure
- No Spring annotations in domain layer
- Infrastructure adapters implement ports defined in application layer

## Spring Boot
- Use constructor injection, never field injection
- Keep controllers thin — delegate to application services
- Use appropriate HTTP status codes (202 for async operations)

## Testing
- JUnit 5 + Spring Boot Test
- Testcontainers for integration tests
- ArchUnit for architecture validation

## General
- No commented-out code
- No System.out.println — use SLF4J logging
- Conventional commits (feat, fix, refactor, test, ci, docs, perf, chore)
