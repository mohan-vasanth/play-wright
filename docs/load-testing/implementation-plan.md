# Load Testing Implementation Plan

## Goal

Improve the load-testing dashboard so that:

- each requested job gets the correct payload
- each form is filled with verified values
- each created job is tracked with a real Job ID
- each submission status reflects the real application outcome
- reports match actual execution, not attempted execution

## Current Implementation Summary

Current entry point:

- [LoadTestingDashboardController.java](/d:/Mohan/play-wright/src/main/java/com/automation/playwright_framework/LoadTestingDashboardController.java:1)

Current execution core:

- [LoadTestingDashboardService.java](/d:/Mohan/play-wright/src/main/java/com/automation/playwright_framework/LoadTestingDashboardService.java:285)

Current browser behavior:

- one shared browser session is authenticated
- parallel workers are launched
- each worker creates a page from the shared browser context
- each worker fills and submits one job

## Main Gaps

### Gap 1: Shared-state and shared-session risk

Risk:

- one shared authenticated browser context is used for many workers
- parallel tab activity can interfere with correctness
- debugging wrong-field or missing-field behavior becomes difficult

Impact:

- wrong values in a tab
- missing selections
- unstable submit behavior

### Gap 2: Active UI concurrency is too aggressive

Risk:

- the current model allows large simultaneous headed-tab execution
- 100 active UI tabs is not a safe correctness model

Impact:

- timeouts
- stale locators
- rendering lag
- non-deterministic field loss

### Gap 3: Field interaction is not equivalent to app acceptance

Risk:

- a field can be typed but still remain invalid in application state
- select controls can show a visible value without a committed selection

Impact:

- pre-submit state is unreliable
- job remains `DRF` or fails validation after submit

### Gap 4: Validation detail is too generic

Risk:

- reports currently collapse some field failures into generic labels such as `Select`

Impact:

- operators cannot identify the exact broken field for failed jobs

### Gap 5: Reporting is improved, but not fully actionable at scale

Risk:

- job result rows contain status and evidence, but not enough control-level detail

Impact:

- root-cause analysis is slow for large runs

## Target Architecture

### Recommended execution model

Use a bounded worker-pool model instead of `100 fully active headed tabs at once`.

Recommended shape:

- requested jobs: `100`
- active browser worker tabs: configurable pool such as `10`
- execution source: job queue
- worker responsibility: one tab processes one queued job at a time until queue is empty

Result:

- 100 jobs can still be processed
- UI correctness becomes much more stable
- machine resource pressure is reduced
- failures are easier to reproduce

### Recommended isolation model

Preferred order:

1. isolate worker state as much as possible
2. avoid shared mutable data between workers
3. if shared authentication is retained, use it only for bootstrapping and not as a reason to share mutable runtime state

## Implementation Phases

## Phase 1: Stabilize execution architecture

### Objective

Make the browser workflow deterministic enough to support large requested-job counts.

### Changes

- replace unrestricted worker launch with a bounded queue-driven worker pool
- decouple `totalUsers` from `activeTabs`
- ensure each worker owns its tab lifecycle
- keep tab number stable for the worker that processes that job

### Primary files

- [LoadTestingDashboardService.java](/d:/Mohan/play-wright/src/main/java/com/automation/playwright_framework/LoadTestingDashboardService.java:285)

### Acceptance criteria

- `100 requested jobs` does not mean `100 simultaneous active UI tabs`
- active tab count is bounded by configuration
- each job is processed exactly once

## Phase 2: Eliminate shared mutable payload risk

### Objective

Guarantee that every worker receives an isolated payload instance.

### Changes

- audit payload preparation and personalization
- ensure all payload mutation happens on a worker-local deep copy
- prevent any handler or helper from storing mutable per-job data in shared service fields
- audit declaration page helpers for mutable shared state

### Primary files

- [LoadTestingDashboardService.java](/d:/Mohan/play-wright/src/main/java/com/automation/playwright_framework/LoadTestingDashboardService.java:1062)
- [IptDeclarationPage.java](/d:/Mohan/play-wright/src/main/java/com/automation/IptDeclarationPage.java:1)
- [OutDeclarationPage.java](/d:/Mohan/play-wright/src/main/java/com/automation/OutDeclarationPage.java:1)
- [CooDeclarationPage.java](/d:/Mohan/play-wright/src/main/java/com/automation/CooDeclarationPage.java:1)

### Acceptance criteria

- no worker can overwrite another worker's payload values
- invoice, message reference, and item-level references remain consistent per job

## Phase 3: Add application-state verification after every fill

### Objective

Do not trust interaction success alone.

### Changes

- verify text input values after fill
- verify dropdown selected text or selected option value after selection
- verify dynamic controls after async load completes
- introduce field-type-specific commit logic for:
  - text inputs
  - native select controls
  - Clarity select controls
  - autocomplete/lookup controls
- record exact control identity when verification fails

### Primary files

- [IptDeclarationPage.java](/d:/Mohan/play-wright/src/main/java/com/automation/IptDeclarationPage.java:2678)
- [OutDeclarationPage.java](/d:/Mohan/play-wright/src/main/java/com/automation/OutDeclarationPage.java:1)
- [CooDeclarationPage.java](/d:/Mohan/play-wright/src/main/java/com/automation/CooDeclarationPage.java:1)

### Acceptance criteria

- every required field is either verified or the job fails immediately with exact field diagnostics
- no job reaches submission with a known invalid required control

## Phase 4: Strengthen pre-submit validation

### Objective

Catch invalid form state before submission and surface exact missing controls.

### Changes

- replace generic invalid-field extraction with structured validation details
- capture:
  - section name
  - field label
  - DOM id
  - form control name
  - attempted value
  - app validation message
- fail fast when pre-submit validation still shows unresolved required fields after retry

### Primary files

- [LoadTestingDashboardService.java](/d:/Mohan/play-wright/src/main/java/com/automation/playwright_framework/LoadTestingDashboardService.java:492)
- [IptDeclarationPage.java](/d:/Mohan/play-wright/src/main/java/com/automation/IptDeclarationPage.java:140)

### Acceptance criteria

- report never shows only `Select` when a concrete control can be identified
- pre-submit failures are actionable without manual reproduction

## Phase 5: Make success criteria strict and consistent

### Objective

Use real application milestones for status transitions.

### Rules

- `Created` only when Job ID exists
- `Submitted` only when submit is verified and application status is successful
- `Failed` only when a real failure or unresolved validation issue exists
- `Pending` only for incomplete tracking states

### Changes

- keep current verified counters
- audit every result path so no early-success state leaks into reporting
- standardize all fallback `WorkflowResult` construction paths

### Primary files

- [LoadTestingDashboardService.java](/d:/Mohan/play-wright/src/main/java/com/automation/playwright_framework/LoadTestingDashboardService.java:696)
- [LoadTestingDashboardService.java](/d:/Mohan/play-wright/src/main/java/com/automation/playwright_framework/LoadTestingDashboardService.java:1776)
- [LoadTestingDashboardService.java](/d:/Mohan/play-wright/src/main/java/com/automation/playwright_framework/LoadTestingDashboardService.java:2136)

### Acceptance criteria

- summary counts always equal the actual per-job outcomes
- `Jobs Created Successfully` equals number of rows with real Job IDs
- `Jobs Submitted Successfully` equals number of rows with verified submission success

## Phase 6: Improve evidence capture

### Objective

Preserve the exact failure state for debugging.

### Changes

- capture screenshot immediately at validation or submit failure
- save raw diagnostics before any navigation away from the failed form
- keep post-failure navigation optional and secondary

### Primary files

- [LoadTestingDashboardService.java](/d:/Mohan/play-wright/src/main/java/com/automation/playwright_framework/LoadTestingDashboardService.java:1810)

### Acceptance criteria

- every failed job has first-failure evidence
- screenshots represent the real invalid form state

## Phase 7: Improve operator-facing reporting

### Objective

Make large-run failure review practical.

### Changes

- extend per-job report row with:
  - tab number
  - worker slot number
  - job number
  - job ID
  - selected JSON
  - payload record number
  - creation status
  - submission status
  - application status
  - exact failed field details
  - screenshot link
  - execution time
- separate summary cards into:
  - requested jobs
  - created jobs
  - submitted jobs
  - failed jobs
  - pending/unverified jobs

### Primary files

- [LoadTestingDashboardService.java](/d:/Mohan/play-wright/src/main/java/com/automation/playwright_framework/LoadTestingDashboardService.java:2136)
- [load-testing-dashboard.html](/d:/Mohan/play-wright/src/main/resources/static/load-testing-dashboard.html:1)

### Acceptance criteria

- an operator can identify exactly why any failed job failed
- report values reconcile with per-job detail rows

## Phase 8: Separate UI correctness from protocol load

### Objective

Prevent JMeter success from being confused with browser workflow success.

### Changes

- keep JMeter as an optional parallel performance signal
- do not use JMeter metrics in browser workflow success calculations
- label the dashboard clearly:
  - `Browser Workflow Result`
  - `JMeter HTTP Load Result`

### Primary files

- [LoadTestingDashboardController.java](/d:/Mohan/play-wright/src/main/java/com/automation/playwright_framework/LoadTestingDashboardController.java:1)
- [LoadTestingDashboardService.java](/d:/Mohan/play-wright/src/main/java/com/automation/playwright_framework/LoadTestingDashboardService.java:177)
- [load-testing-dashboard.html](/d:/Mohan/play-wright/src/main/resources/static/load-testing-dashboard.html:1)

### Acceptance criteria

- browser job correctness is reported independently from JMeter throughput

## Recommended Order of Work

1. Phase 1
2. Phase 2
3. Phase 3
4. Phase 4
5. Phase 5
6. Phase 6
7. Phase 7
8. Phase 8

## Delivery Definition

The implementation is complete when:

- requested jobs are processed through a stable worker model
- payload data is isolated per job
- every required field is verified against application state
- failed jobs show exact missing-field detail
- created/submitted counts match real verified outcomes
- job result rows are trustworthy enough to debug a 100-job run without guessing
