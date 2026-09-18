# Agent instructions — Archaeo

This file applies to all work in this repository.

## Language

Use **English** for everything that lives in the codebase.

## Javadoc (XML documentation)

Every type and every method must have a Javadoc comment so the next reader knows **why it exists**, not only what it does.

```java
/**
 * Builds hidden find shapes for one administered ruin.
 *
 * @param chunk chunk that will own the site
 * @return persisted site; terrain is not modified
 */
```

Required on methods: a short purpose sentence, `@param` for each parameter, `@return` when not `void`, `@throws` when the method fails on purpose.

Do not leave undocumented public API. Prefer documenting private helpers too when they encode design rules (strata, shapes, YAML load).

## Commit name after code changes

After every user request that **modifies code**, end the reply with a **proposed git commit subject**. Do this even if the user did not ask to commit.

- Propose; **do not** run `git commit` unless the user explicitly asks.
- One primary line in English, imperative mood, focused on **why** (not a file list). Keep it short and concise.
- Skip the proposal when the turn only answered a question and left the working tree unchanged.

On demand, the user can run `/suggest-commit` (see `.cursor/commands/suggest-commit.md`) to suggest names from `git diff` without relying on this turn’s edits.
