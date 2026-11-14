# Rollout Stage Validation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `POST /api/v1/rollouts` rejects invalid stage percentages and failure thresholds with a 400 that names the problem, and the dashboard shows that reason instead of silently fixing the input.

**Architecture:** A custom Bean Validation constraint `@ValidStagePercentages` on `CreateCampaignRequest.stagePercentages` runs in the controller's existing `@Valid`, so errors use the existing validation 400 format. The threshold gets standard range annotations. The dashboard client turns `details` into the error message, and the rollout form stops filtering.

**Tech Stack:** Java 21, Spring Boot 3.3, Jakarta Validation (Hibernate Validator), MockMvc standalone, JUnit 5 + AssertJ; React + TypeScript, Vitest.

**Spec:** `docs/superpowers/specs/2026-11-15-rollout-stage-validation-design.md`

## Global Constraints

- **Stage rules** (only when the list is non-null and non-empty): whole numbers 1–100, no nulls, strictly increasing, last == 100. A null or empty list stays valid, and the service keeps defaulting it to `5, 25, 100`.
- **Error messages, first problem wins, checked in this order:**
  1. `stages must be whole numbers from 1 to 100` for a null entry;
  2. `stages must be whole numbers from 1 to 100 (got X)` for an out-of-range value;
  3. `stages must increase: X follows Y`;
  4. `the last stage must be 100`.
- **Threshold:** `failureThresholdPercent` from 0 to 100 inclusive (`@DecimalMin("0")`, `@DecimalMax("100")`). Missing still means 10.
- **Error format:** the existing one: HTTP 400, `message: "Validation failed"`, `details: ["stagePercentages: <message>", …]`.
- Commits as `basarsy <basarsoyturk03@gmail.com>`, no Claude/Anthropic attribution.
- Backend tests: `./mvnw -B -ntp -pl pulse-monolith test -Dtest=<Class>`. Dashboard: `npm test`, `npm run build` in `pulse-dashboard`.

## Review Focus

1. A list with a `null` element (the dashboard sends `NaN` as `null` in JSON) → 400 with the whole-number message, not a 500 or an NPE inside the validator. → Task 1 test.
2. Duplicates such as `25, 25, 100` → "must increase", because a repeated stage instructs nobody. → Task 1 test.
3. A single stage `100` is valid, and so is `1, 100`. → Task 1 test.
4. The wiring is real: a bad request through the controller is rejected before the service runs, with the detail prefixed by `stagePercentages:`. → Task 1 MockMvc test.
5. An error body without `details`, such as a 409 or 404, still shows its `message` in the dashboard. → Task 2 test.

---

### Task 1: Backend constraint and threshold range

**Files:**
- Create: `pulse-monolith/src/main/java/com/pulse/connected/ota/dto/ValidStagePercentages.java`
- Create: `pulse-monolith/src/main/java/com/pulse/connected/ota/dto/StagePercentagesValidator.java`
- Modify: `pulse-monolith/src/main/java/com/pulse/connected/ota/dto/CreateCampaignRequest.java`
- Test: `pulse-monolith/src/test/java/com/pulse/connected/ota/dto/CreateCampaignRequestTest.java`
- Test: `pulse-monolith/src/test/java/com/pulse/connected/ota/RolloutCampaignControllerValidationTest.java`

**Interfaces:**
- Produces: `@ValidStagePercentages`, targeting `FIELD` and `PARAMETER`, retention `RUNTIME`, `@Constraint(validatedBy = StagePercentagesValidator.class)`, default message `invalid stage percentages`.
- Produces: `StagePercentagesValidator implements ConstraintValidator<ValidStagePercentages, List<Integer>>`. Its `isValid` disables the default violation and adds one violation with the specific message.

- [ ] **Step 1: Write the failing tests.**
  - `CreateCampaignRequestTest`, using `Validation.buildDefaultValidatorFactory().getValidator()`. A helper `messages(List<Integer> stages, Double threshold)` returns violation messages:
    - `defaultStages_AreValid`: `null` and `List.of()` → no violations.
    - `validStages`: `5,25,100`, `100` and `1,100` → none.
    - `nullEntry_Rejected`: `Arrays.asList(5, null, 100)` → `["stages must be whole numbers from 1 to 100"]`.
    - `outOfRange_Rejected`: `0,100` → `["stages must be whole numbers from 1 to 100 (got 0)"]`; `5,150` → `(got 150)`.
    - `notIncreasing_Rejected`: `50,25,100` → `["stages must increase: 25 follows 50"]`; `25,25,100` → `["stages must increase: 25 follows 25"]`.
    - `lastNotHundred_Rejected`: `5,25` → `["the last stage must be 100"]`.
    - `threshold_Range`: `-1` and `100.5` each give one violation on `failureThresholdPercent`; `0` and `100` are valid.
  - `RolloutCampaignControllerValidationTest`: `MockMvcBuilders.standaloneSetup(new RolloutCampaignController(mock(RolloutCampaignService.class))).setControllerAdvice(new GlobalExceptionHandler()).build()`.
    - `invalidStages_Return400WithReason`: POST `/api/v1/rollouts` with `{"softwareVersionId":"<uuid>","targetModel":"iX","stagePercentages":[50,25,100]}` → status 400, `$.message == "Validation failed"`, `$.details[0] == "stagePercentages: stages must increase: 25 follows 50"`. Then `verifyNoInteractions(service)`.

    Check the controller's `@RequestMapping` base path before writing the URL.

- [ ] **Step 2: Run tests to verify they fail**
  Run: `./mvnw -B -ntp -pl pulse-monolith test -Dtest='CreateCampaignRequestTest,RolloutCampaignControllerValidationTest'`
  Expected: compilation failure (no `@ValidStagePercentages` yet), or, if staged, assertion failures (no violations).

- [ ] **Step 3: Implement** the annotation and validator (rule order and messages as in Global Constraints), and annotate the two fields.

- [ ] **Step 4: Run tests to verify they pass**
  Run: `./mvnw -B -ntp -pl pulse-monolith test -Dtest='CreateCampaignRequestTest,RolloutCampaignControllerValidationTest,RolloutCampaignServiceTest,KafkaRoundTripTest'`
  Expected: PASS. The round-trip test creates a valid campaign through the API.

- [ ] **Step 5: Commit**
  ```bash
  git add pulse-monolith/src
  git commit -m "feat(ota): validate rollout stages and failure threshold"
  ```

---

### Task 2: Dashboard shows the reason; README

**Files:**
- Modify: `pulse-dashboard/src/api/client.ts` (`request`: error message)
- Test: `pulse-dashboard/src/api/client.test.ts`
- Modify: `pulse-dashboard/src/pages/RolloutListPage.tsx:61-74` (`submit`)
- Modify: `README.md` (the "Fleet OTA Updates" bullet under Key Capabilities)

**Interfaces:**
- Consumes: the 400 body shape from Task 1 (`message`, `details: string[]`).
- Produces: `ApiError.message` is `details.join('; ')` when `details` is a non-empty array, otherwise `message` as today.

- [ ] **Step 1: Write the failing tests** in `client.test.ts`:
  - `uses validation details as the error message`: `fetch` resolves `json(400, { message: 'Validation failed', details: ['stagePercentages: the last stage must be 100'] })` → `request('/rollouts', { method: 'POST' })` rejects with an `ApiError` whose message is `'stagePercentages: the last stage must be 100'` and whose status is 400.
  - `falls back to message without details`: `json(409, { message: 'Cannot start campaign in status: COMPLETED' })` → the message is exactly that.

- [ ] **Step 2: Run tests to verify they fail**
  Run: `npm test` in `pulse-dashboard`. Expected: the first new test fails (the message is `'Validation failed'`).

- [ ] **Step 3: Implement.**
  - `client.ts`: build the message from `details` as in Interfaces.
  - `RolloutListPage.submit`: split on commas and trim. If any token is not a whole number (`!/^\d+$/.test(token)`), show `Stages must be whole numbers, e.g. 5, 25, 100` locally and return. Otherwise send `tokens.map(Number)` unfiltered; the server decides range, order and ending at 100.
  - README: append to the "Fleet OTA Updates" bullet that stage percentages must be increasing whole numbers ending at 100, and that invalid stages are rejected with a 400.

- [ ] **Step 4: Run tests to verify they pass**
  Run: `npm test` and `npm run build` in `pulse-dashboard`. Expected: all tests pass, and the build has no type errors.

- [ ] **Step 5: Commit**
  ```bash
  git add pulse-dashboard/src README.md
  git commit -m "feat(dashboard): show why a rollout was rejected instead of filtering stages"
  ```

---

### Final verification

- [ ] With Docker running: `./mvnw -B -ntp verify` → `BUILD SUCCESS`; and `npm test && npm run build` in `pulse-dashboard`.
- [ ] `git log --format='%an <%ae> | %cn <%ce>%n%B' main..HEAD`: author and committer are the user, with no Claude/Anthropic mention.
