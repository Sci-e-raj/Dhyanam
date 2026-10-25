# Dhyanam (FocusBuddy)

A local Android app project for FocusBuddy.

## Gradle setup

The project uses Gradle 9.6.0 through the checked-in Gradle wrapper. The wrapper is configured to download the official distribution from `https://services.gradle.org/distributions/gradle-9.6.0-bin.zip`.

### Local workaround for network timeouts

If the official distribution cannot be reached because of a network timeout, a developer may temporarily point `distributionUrl` in `gradle/wrapper/gradle-wrapper.properties` to a local Gradle 9.6.0 ZIP. For example:

```properties
distributionUrl=file:///C:/Gradle/gradle-9.6.0-bin.zip
```

That path is specific to one Windows machine and must not be committed as the project's shared wrapper configuration. Before another person builds the project, restore the official URL shown above and verify Gradle sync/build in their environment. Keep the wrapper scripts and wrapper JAR in version control.
