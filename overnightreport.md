## 2026-02-11 08:26:01 UTC

### Summary
- **Security**: Removed committed secrets from `variables.env` (set values blank), added `variables.env.example`, and hardened `.gitignore` to prevent future secret commits.
- **Logging**: Stopped logging OpenAI API key prefixes/lengths. Replaced `System.out.println` boot-time env messages with structured `slf4j` logging.
- **Config safety**: Avoided shipping `DEBUG` config logging by default; `logging.level.org.springframework.boot.context.config` is now controlled by `LOG_LEVEL_SPRING_CONFIG` (defaults to `INFO`).
- **Error handling**: Added a minimal safeguard to avoid leaking secret-like strings in API error responses.

### Notes / Rationale
- The repo contained real API keys in `variables.env`. This is high-risk (leak via git history/logs) and can also cause environment drift.
- `OpenAiService` previously logged API key prefixes and lengths; those can end up in hosted logs and should be treated as sensitive.

### Verification
- Ran `mvn test` and `mvn package` successfully after the refactor.


