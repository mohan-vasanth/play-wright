# Load Testing Validation Checklist

## Purpose

Use this checklist during implementation and rollout to verify that the load-testing dashboard behaves correctly.

## Functional Validation

- Start a run with `1 requested job` and verify one job is created with one real Job ID.
- Start a run with `1 requested job` and verify submission status is only `SUBMITTED` when the application confirms success.
- Start a run with `1 requested job` and force a validation failure. Verify the report shows `CREATED` plus `FAILED` when a draft Job ID exists.
- Verify the selected JSON record is the one actually used for the job.
- Verify per-job `Tab Number`, `Job Number`, and `JSON Record Number` are correct.

## Data Isolation Validation

- Run `2 jobs` with payload personalization enabled and verify both message references are unique.
- Verify invoice number personalization is consistent between header-level invoice data and item-level invoice references.
- Verify one worker cannot overwrite another worker's payload-derived values.

## UI State Validation

- For text fields, verify `actual input value == expected value`.
- For dropdowns, verify `selected option text/value == expected value`.
- For lookup/autocomplete controls, verify the application-selected state and not just the visible typed text.
- For async-loaded controls, verify the field remains valid after AJAX completion.

## Failure Evidence Validation

- On a failed field, capture screenshot before navigation away from the form.
- Verify failure report includes:
  - section
  - field label
  - DOM id or form control name
  - attempted value
  - app validation message
- Verify raw diagnostics are retained in logs.

## Reporting Validation

- `Total Requested Jobs` must equal request payload job count.
- `Jobs Created Successfully` must equal number of jobs with real Job IDs.
- `Jobs Submitted Successfully` must equal number of jobs with verified submission success.
- `Jobs Failed` must equal number of jobs with real failed outcomes.
- Report totals must be derived from per-job verified results, not tab count.

## Concurrency Validation

- Run with worker pool size `1` and confirm deterministic success/failure behavior.
- Run with worker pool size `5` and compare results against pool size `1`.
- Run with worker pool size `10` and verify there is no payload crossover.
- Do not certify `100 simultaneous headed tabs` as supported unless machine-level evidence proves it stable.

## Recommended Certification Path

1. Certify `1 job`.
2. Certify `5 jobs` with small worker pool.
3. Certify `10 jobs` with bounded worker pool.
4. Certify `25 jobs`.
5. Certify `50 jobs`.
6. Certify `100 requested jobs` processed through the approved worker pool configuration.

## Release Gate

Do not mark the implementation complete until all of the following are true:

- no known shared mutable state remains across workers
- no known invalid field reaches submission without being reported
- per-job results are reproducible across repeated runs
- report summary and detail rows reconcile exactly
- failure evidence is captured for every failed job
