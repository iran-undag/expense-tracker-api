# JaCoCo Coverage Design

## Goal

Add opt-in JaCoCo coverage reporting to the single-module Maven build so local
and CI SonarQube analysis can import Java line and branch coverage.

The implementation succeeds when:

- the existing test command continues to run without coverage instrumentation;
- the coverage profile runs the same test suite with JaCoCo instrumentation;
- Maven writes a non-empty XML report to
  `target/site/jacoco/jacoco.xml`;
- the existing Byte Buddy test agent and JVM `--add-opens` arguments remain
  active; and
- SonarScanner for Maven imports the report from its standard location without
  a custom report-path property.

## Scope

The implementation changes:

- `pom.xml`, to add the JaCoCo version, agent argument composition, and coverage
  profile; and
- `README.md`, to document the normal test command, coverage command, report
  location, and SonarQube invocation.

The implementation does not change:

- application source or test behavior;
- the Docker image or runtime;
- GitHub Actions or deployment ordering;
- SonarQube quality-gate thresholds;
- coverage exclusions; or
- the existing SonarQube server configuration.

CI integration can consume the profile later by running the same Maven command
documented here.

## Selected Approach

Use an explicit Maven profile named `coverage`.

This keeps the ordinary developer test loop unchanged:

```bash
./mvnw test
```

Coverage is generated only when requested:

```bash
./mvnw clean verify -Pcoverage
```

Always-on instrumentation was rejected because it adds coverage overhead to
every local test run. A command-line-only JaCoCo setup was rejected because the
build already owns Surefire's `argLine`; composing both Java agents in the POM
is more reliable and discoverable.

## Maven Configuration

### Versions and inactive-profile safety

Add a pinned `jacoco.version` property with version `0.8.15`.

Add an empty `jacocoArgLine` property outside the profile. Surefire uses late
property evaluation for this value. The empty default ensures the normal test
command does not pass a literal unresolved placeholder when the coverage
profile is inactive.

### Surefire agent composition

Preserve the existing Surefire configuration and prepend:

```xml
@{jacocoArgLine}
```

to the existing `argLine`.

The resulting test JVM argument order is:

1. JaCoCo's agent argument when the coverage profile is active;
2. the existing Byte Buddy agent; and
3. the existing `java.lang` and `java.util` module-opening arguments.

No existing test JVM argument is removed or reformatted beyond what is required
to compose the agents.

### Coverage profile

Add a `coverage` profile containing `jacoco-maven-plugin`.

The plugin has two executions:

1. `prepare-agent` runs in JaCoCo's default `initialize` phase and writes its
   Java agent argument to the `jacocoArgLine` property.
2. `report` runs in the `verify` phase and emits the XML report.

Configure the report execution to emit XML only. SonarQube imports
`target/site/jacoco/jacoco.xml` automatically, so the POM will not define
`sonar.coverage.jacoco.xmlReportPaths`.

## Data Flow

When the profile is active:

1. Maven initializes the build.
2. JaCoCo prepares its Java agent argument.
3. Surefire starts the test JVM with both JaCoCo and Byte Buddy agents.
4. Tests execute and JaCoCo records execution data.
5. The `verify` phase converts the execution data to XML.
6. A subsequent SonarScanner goal imports the standard XML report and uploads
   coverage with the static-analysis result.

The combined local analysis command is:

```bash
./mvnw clean verify -Pcoverage \
  org.sonarsource.scanner.maven:sonar-maven-plugin:5.5.0.6356:sonar
```

`SONAR_HOST_URL`, `SONAR_TOKEN`, and the project key remain external
SonarQube configuration concerns.

## Failure Behavior

- A test failure fails the Maven build before a successful analysis can be
  treated as deployable.
- If the coverage profile is inactive, `jacocoArgLine` resolves to an empty
  value and tests use only the existing JVM arguments.
- If JaCoCo cannot attach or generate its report, the coverage-profile build
  fails or the required report verification fails; it must not be presented as
  successful coverage generation.
- SonarQube connectivity or authentication failures remain scanner failures and
  do not affect ordinary Maven test commands.

## Verification

Run these checks after implementation:

```bash
./mvnw test
./mvnw clean verify -Pcoverage
test -s target/site/jacoco/jacoco.xml
git diff --check
```

Inspect the coverage build output or effective Surefire configuration to
confirm that the test JVM receives both the JaCoCo and Byte Buddy agents.

If valid SonarQube connection settings are available, run:

```bash
./mvnw clean verify -Pcoverage \
  org.sonarsource.scanner.maven:sonar-maven-plugin:5.5.0.6356:sonar
```

and confirm that the SonarQube project displays imported line and branch
coverage. Lack of server credentials does not block local verification of the
JaCoCo XML report.

## Documentation

Update the README with:

- the unchanged fast test command;
- the opt-in coverage command;
- the standard XML report path;
- the combined coverage and SonarQube command; and
- a note that coverage thresholds are managed by SonarQube rather than Maven
  in this initial implementation.
