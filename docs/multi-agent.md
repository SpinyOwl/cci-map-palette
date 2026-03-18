# Multi-Agent Workflow

This document explains how to split work in this repository when multiple agents are involved.

## Roles

### Coordinator

The coordinator is the single source of truth for task planning and final merge decisions.
It should:

- Read the request and identify the affected areas.
- Split work between workers based on file ownership.
- Prevent duplicated edits and conflicting assumptions.
- Validate that the final result is consistent across docs, code, and pack data.

### Worker 1 and Worker 2

Both workers are universal. They can be assigned any repo task.

The coordinator decides placement based on:

- Current workload
- File overlap risk
- Needed context
- How best to keep the change isolated

## Suggested Division of Labor

If a task touches multiple areas, use this split:

1. Coordinator defines the expected output and file boundaries.
2. Worker 1 handles one slice of the work.
3. Worker 2 handles the other slice of the work.
4. Coordinator reviews the combined result and checks for mismatches.

## Handoff Checklist

Each worker should return:

- Files changed
- Behavior changed
- Commands run
- Open issues or assumptions

The coordinator should verify:

- Generated output still matches the intended pack format
- Build tasks still reflect the documented workflow
- Documentation stays aligned with the actual repo layout

## Ownership Map

- No worker has a fixed ownership boundary.
- The coordinator may assign any path to either worker.
- `README.md`, `docs/`, and root build files are integration surfaces and should be reviewed by the coordinator when changed.

## Validation Guidance

- Use targeted Gradle tasks instead of full rebuilds when possible.
- Regenerate outputs only when source logic or pack data changed.
- Avoid committing files from `build/` unless explicitly requested.
