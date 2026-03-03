# Repository Guidelines

## Project Structure & Module Organization
This is a multi-module Maven project:
- `flowreplay-core/`: core domain models, storage, replay engine, comparison and report generation.
- `flowreplay-proxy/`: Netty-based HTTP/TCP proxy servers and handlers.
- `flowreplay-cli/`: command-line entrypoint (`FlowReplayCLI`) and packaging.
- `docs/`: report examples and supporting documentation assets.
- Root `pom.xml`: aggregator for all modules.

Source and tests follow Maven conventions:
- Production code: `*/src/main/java/...`
- Tests: `*/src/test/java/...`

## Build, Test, and Development Commands
- `mvn clean package`: build all modules and produce CLI fat jar.
- `mvn -pl flowreplay-core,flowreplay-proxy test`: run core/proxy tests only.
- `mvn -pl flowreplay-cli -DskipTests package`: rebuild CLI artifact quickly.

Run locally with explicit Java path (recommended across servers):
```bash
<JAVA_BIN> -jar flowreplay-cli/target/flowreplay-cli-1.0.0-SNAPSHOT-jar-with-dependencies.jar --help
<JAVA_BIN> -jar flowreplay-cli/target/flowreplay-cli-1.0.0-SNAPSHOT-jar-with-dependencies.jar rr --port 8080 --target localhost:8081 --output ./recordings --replay-target http://localhost:9090
```

## Coding Style & Naming Conventions
- Java 21, UTF-8, 4-space indentation.
- Class/record names: `UpperCamelCase`; methods/fields: `lowerCamelCase`; constants: `UPPER_SNAKE_CASE`.
- Keep package names under `com.flowreplay.<module>...`.
- Prefer explicit imports (no wildcard imports) and small, focused methods.
- Follow existing logging style (`slf4j`) and avoid adding noisy logs in hot paths.

## Testing Guidelines
- Framework: JUnit 5 (Surefire).
- Naming: `*Test` for unit tests, `*IntegrationTest` for integration-level behavior.
- Mirror production package structure under `src/test/java`.
- Add/adjust tests for any replay, proxy routing, or CLI option behavior change.
- Targeted run example: `mvn -pl flowreplay-proxy -Dtest=HttpProxyHandlerTest test`.

## Commit & Pull Request Guidelines
- Current history uses concise, action-oriented Chinese subjects (for example: `修复...`, `新增...`, `文档更新`).
- Keep one logical change per commit; mention affected module(s) when useful.
- PRs should include:
  - change summary and motivation,
  - impacted modules/files,
  - verification commands and key results,
  - sample CLI output or report screenshot when behavior/reporting changes.
- If CLI behavior changes, update `README.md` in the same PR.

## Security & Configuration Tips
- Do not commit real captured traffic containing credentials or personal data.
- Keep environment-specific endpoints/ports in local commands, not hardcoded in source.
