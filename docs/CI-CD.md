# CI-CD.md
- Run `./gradlew assembleDebug test` on every PR.
- Cache Gradle and Android build artifacts.
- Require tests to pass before merge.
- Optional: GitHub Actions workflow `android.yml` included later.
