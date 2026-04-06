# Documentation Layout

This project separates public and internal documentation under `docs/`.

## Public Docs (tracked)

- `docs/public/`: user-facing and developer-facing documents intended to be versioned in git.

Current public docs:

- `docs/public/region-format.md`: Anvil region format notes and ChronoVault integration details.

## Internal Docs (ignored)

- `docs/internal/`: local notes, drafts, and workflow artifacts that should not be committed.

Ignored subfolders are defined in `.gitignore`:

- `docs/internal/`
- `docs/drafts/`
- `docs/tmp/`

If you need to publish a document from internal notes, move it into `docs/public/`.
