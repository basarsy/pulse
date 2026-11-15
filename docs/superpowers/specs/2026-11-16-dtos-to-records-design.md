# DTOs to Java records

## Why

The backend's 25 DTO classes are about 1,850 lines of hand-written fields,
getters, setters and builders. Pulse is a portfolio project on Java 21, so
this boilerplate is the first thing a reviewer reads in the backend. Records
say the same thing in a few lines each.

## Goals

- Turn every class in `pulse-monolith/src/main/java/com/pulse/connected/*/dto/`
  that only carries data into a `record`. That is 25 classes: requests,
  responses, Kafka events and `ErrorResponse`.
- Delete every hand-written builder, getter and setter in them.
- Keep the JSON exactly the same on the REST API, Kafka and WebSocket.

## Non-goals

- JPA entities (`Vehicle`, `User`, `RolloutCampaign`, …). JPA entities can't
  be records, so they keep their builders.
- `StagePercentagesValidator`, `StrictIntegerDeserializer` and
  `ValidStagePercentages` are not DTOs and stay as they are.
- No behaviour changes and no renamed fields.

## Anti-goals

- Changing the JSON in any way: a renamed field, a field that goes missing,
  or a different default. That would break the dashboard or Kafka consumers
  without any visible error.

## Approaches

1. **Records with canonical constructors only (picked).** All builders go
   away and call sites use `new X(...)`. This removes the most boilerplate,
   which is the point of the change. Long constructor calls (12 to 15
   arguments) are written one argument per line.
2. Records plus small builders on the 4 biggest. Call sites read better,
   but about 150 lines of builder code come back. Rejected because it keeps
   the boilerplate we're removing.
3. Lombok `@Builder` on records. Rejected because the project has avoided
   Lombok and this would add a dependency.

## Design

- **Guard the JSON first.** Before converting anything, add one test that
  serializes a fully populated instance of every DTO with the app's
  `ObjectMapper` and compares it with a JSON file saved under
  `src/test/resources`. Request DTOs also get a test that deserializes them,
  including the missing-field defaults. The test passes on the old classes,
  then must pass unchanged on the records.
- **Guard the mappers.** Several records have neighbouring components of
  the same type (for example three `long` counters in `CampaignResponse`),
  so a swapped constructor argument would still compile. The JSON test
  builds DTOs by hand and can't catch that. Each big response is built in
  exactly one place: `CommandService`, `RolloutCampaignService` (campaign
  and vehicle update status), `SoftwareVersionService`, `TelemetryService`
  and `VehicleService`. For each one, a test fills the source with a
  different value in every field and checks that each value ends up in the
  matching response field. Like the JSON test, it is written and passes
  before the conversion.
- **Defaults** (`CreateCampaignRequest.failureThresholdPercent` = 10.0,
  `CreateSoftwareVersionRequest.mandatory` = false) move into compact
  constructors. A `null` threshold becomes 10.0, and `mandatory` is a
  primitive, so a missing value is already false.
- **Validation annotations** (`@NotNull`, `@NotBlank`, `@Size`,
  `@ValidStagePercentages`, `@JsonDeserialize(contentUsing = …)`, …) move
  onto the record components. Bean Validation and Jackson both read them
  there.
- **Call sites:** `getX()` becomes `x()`, and `isMandatory()` becomes
  `mandatory()`. `X.builder()…build()` becomes `new X(...)`. Tests are
  updated the same way.
- **Lists** in records stay as passed in. I'm not adding defensive copies,
  so behaviour stays the same.

## Verification

The full suite passes (unit and Testcontainers integration tests, with
Docker running), including the new JSON test, before and after the change.
After the change, no file under `*/dto/` contains `builder`, `get` or `set`
methods.

## Left to the builder

Order of conversion and how to group the commits.
