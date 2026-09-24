# /scripts

Helper scripts that are *not* part of the build. The build is driven by
`android/configure.sh`, `android/build-variants.sh`, and `.github/workflows/android.yml`.

| Script | Purpose |
|---|---|
| `clean-build.sh` | Wipes `build-android-*` + the Gradle build cache. Useful when a CMake cache invalidation leaves stale objects. |
| `inspect-abi.sh` | Runs `llvm-readelf` against `libpocos3-core.so` to verify the `pocos3_*` symbols are exported. |
| `format-kt.sh` | Runs `ktlint` over the Kotlin source. |
