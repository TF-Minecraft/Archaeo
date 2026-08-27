# Suggest commit names from git diff

Do **not** create a commit. Only propose message text.

## Gather diffs

In the repo root, run in parallel:

- `git status`
- `git diff` (unstaged)
- `git diff --cached` (staged)
- `git log -8 --oneline` (match existing subject style if there is a pattern)

If there is no staged or unstaged change, say so and stop.

Prefer **staged** changes when `--cached` is non-empty; otherwise use the unstaged diff. Mention untracked files from `git status` if they would be part of a typical commit.

## Write the suggestions

- English, imperative subject (~50–72 characters), focused on **why**.
- Lead with `add` / `update` / `fix` / `docs` / `refactor` only when it matches the change.
- Do not invent work that is not in the diff. Ignore secrets and generated `target/` output.

Reply with:

1. **Primary** — the best single-line subject.
2. **Alternatives** — one or two other subjects.
3. **Body (optional)** — one or two sentences if the diff needs more context; omit if the subject is enough.

Do not run `git add` or `git commit`.