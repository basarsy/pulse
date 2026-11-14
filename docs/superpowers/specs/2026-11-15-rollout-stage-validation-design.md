# Rollout stage validation

## Why

`POST /api/v1/rollouts` accepts any `stagePercentages` and `failureThresholdPercent`. Bad input is
accepted and misbehaves later:

- 0%, negative or >100% stages;
- decreasing or repeated stages, which instruct nobody because stages are cumulative shares;
- a `null` entry, which becomes a 500 on insert;
- a threshold of -5 (auto-pause on the first event) or 500 (never pause).

The dashboard hides the problem: it silently drops values outside 1–100, so `5, 200, 100` creates a
`5, 100` rollout. It doesn't check the order either.

## Goals

- **Stage rules.** When `stagePercentages` is present and non-empty:
  - every value is a whole number from 1 to 100 (no nulls);
  - the values are strictly increasing;
  - the last value is **100** (decided: every rollout reaches the whole fleet).

  Otherwise → 400.
- **Default kept.** A missing or empty list keeps meaning the default `5, 25, 100`.
- **Threshold.** `failureThresholdPercent` must be from 0 to 100 inclusive → otherwise 400. Missing
  keeps meaning 10.
- **Errors name the problem.** The 400 uses the existing validation format (`message: "Validation
  failed"`, `details: ["stagePercentages: …"]`), and each detail says what is wrong, e.g.
  `stages must increase: 25 follows 50`.
- **Dashboard.** It stops filtering input. It sends what was typed and shows the server's reasons.

## Non-goals

- Partial rollouts that end below 100%.
- Changing campaigns that already exist. The execution-time clamp from #8 stays as a safety net.
- Validating other forms or endpoints.

## Approaches

- **One custom constraint on the list** (`@ValidStagePercentages` + `ConstraintValidator`):
  **chosen.** All stage rules sit in one place and run in the controller's existing `@Valid`. The
  error is the same 400 format as the other endpoints, with a message that names the problem. The
  rules can be unit-tested with a plain `Validator`.
- *Built-in element constraints plus service checks*: rejected. The rules are split across two
  places, and the failures come back in two error formats.
- *Service-only checks*: rejected. They bypass request validation and return a generic message.

## Design

- **Backend.**
  - `ota/dto/ValidStagePercentages` (annotation) and `StagePercentagesValidator`. A null or empty
    list is valid. Otherwise the validator reports the **first** problem found, in this order:
    1. null entry → `stages must be whole numbers from 1 to 100`;
    2. out of range → same message, naming the value;
    3. not strictly increasing → `stages must increase: X follows Y`;
    4. last ≠ 100 → `the last stage must be 100`.

    It uses `buildConstraintViolationWithTemplate` for the specific message.
  - `failureThresholdPercent` gets `@DecimalMin("0")` and `@DecimalMax("100")`.
- **Dashboard.**
  - `api/client.ts`: when an error body has `details`, the `ApiError` message becomes
    `details.join('; ')`. Otherwise it stays `message` as today. This applies to every form, and
    the existing `client.test.ts` gets a case for it.
  - `RolloutListPage`: parse the comma-separated input. A token that isn't a whole number gets a
    local error, because the text field can't be sent as numbers otherwise *(my call)*. Everything
    else goes to the server unfiltered. The placeholder and default stay `5, 25, 100`.
- **README.** One line: stages are increasing whole percentages ending at 100.

## Left to the builder

Exact message wording beyond the examples above, and test layout, following existing patterns.
