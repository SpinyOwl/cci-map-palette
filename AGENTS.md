# Multi-Agent Working Guide

This repository supports collaborative work by a coordinator agent and two worker agents.
The goal is to keep changes scoped, reduce overlap, and make handoffs explicit.

## Agent Roles

### Coordinator Agent

Owns task decomposition, conflict resolution, and final integration.

Responsibilities:

- Break work into focused sub-tasks before editing files.
- Assign work to the worker whose scope matches the change.
- Review cross-cutting changes for consistency.
- Merge worker output into a single coherent result.
- Run or request validation when changes span multiple areas.

### Worker Agents

Both workers are universal executors.

They do not own fixed task types or fixed path sets. The coordinator assigns work based on current need, overlap risk, and available context.

## Working Rules

- Prefer small, isolated edits.
- Do not modify generated output under `build/` unless a task explicitly requires it.
- Do not touch `local.yml` unless the user asks for local-machine configuration changes.
- Use `apply_patch` for edits.
- Read the existing project docs before adding new behavior or conventions.
- Keep file ownership clear. If a task crosses scopes, the coordinator should own the final pass.
- Do not treat either worker as specialized to a particular subsystem.

## Code Requirements

- Follow clean code principles: keep functions small, names specific, and control flow easy to scan.
- Prefer simple, direct implementations over clever abstractions.
- Reuse existing patterns in the repository before introducing new ones.
- Keep public/private APIs documented with Javadocs or KDoc, especially for classes, functions, and data types that define behavior or contracts.
- Add inline comments only when the code would otherwise be hard to understand.
- Preserve formatting and style consistency with surrounding code.
- Avoid unnecessary renames or refactors that do not support the task.
- Include tests or validation when behavior changes, and note any gaps if validation is not possible.

## Handoff Format

When a worker finishes, it should report:

- Files changed
- What was changed
- Any validation run
- Any follow-up risk or gap

The coordinator should then decide whether to:

- Accept the worker output as-is
- Request one more worker pass
- Perform an integration pass

## Validation

Use the smallest useful check for the area being changed:

- `./gradlew.bat test` or targeted Gradle tasks for Kotlin/build changes
- `./gradlew.bat generateColors` or `./gradlew.bat generateAllColors` for generation work
- `./gradlew.bat packResourcepack` for packaging changes

## Repo Notes

- This project builds an FTB Chunks resource pack for Minecraft mods.
- The source-of-truth pack contents live in `resourcepack/`.
- Kotlin generator logic lives in `generator-core/` and `generator-cli/`.
- `docs/PROJECT.md` captures the current project structure and build entry points.
