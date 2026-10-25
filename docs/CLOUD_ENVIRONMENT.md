# Reproducible cloud environment

Use Linux x86_64, a complete JDK 17 (not a JRE), Android command-line tools and the
checked-in Gradle wrapper. The initial cloud workspace had only a Java 21 JRE.
This task installed Temurin JDK 17 and Android command-line tools build 13114758
outside the repository, under `/workspace/toolchains`.

```sh
export JAVA_HOME=/path/to/jdk17
export ANDROID_HOME=/path/to/android-sdk
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
sdkmanager --licenses
sdkmanager 'platforms;android-34' 'platforms;android-36' 'build-tools;34.0.0' 'build-tools;36.0.0' 'platform-tools'
bash scripts/verify-android-env.sh
bash gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

The baseline uses SDK 34; the final app compiles against SDK 36. The cloud network
policy was inspected: unrestricted HTTP through the configured session proxy,
no VPN. Java tools require the session proxy host/port and system CA trust store
in this environment. Configure these in local Gradle/JVM settings, never in
checked-in credentials. TLS verification stays enabled. Standard GitHub runners
do not need these environment-specific proxy settings.

Local task logs are in `/workspace/build-logs`; generated SDKs, Gradle caches,
signing material and build outputs are excluded from Git. GitHub Actions installs
its own toolchain and runs independently of the cloud cache. There is no attached
Android hardware or KVM device; device-only checks remain explicitly pending.

The available Cloud Environment plugin reports runtime context but has no tool
to publish an environment configuration. The installation/verification scripts
and CI are the reproducible source of setup, rather than a claimed published cache.
