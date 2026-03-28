---
name: commit
description: Stage and commit current changes with a well-crafted commit message following project conventions
---

# Commit Skill

Stage and commit current changes with a concise, descriptive commit message.

## Instructions

### 1. Gather Context

Run these commands in parallel using `Bash`:
- `git status` to see all modified, added, and untracked files
- `git diff` to see unstaged changes
- `git diff --cached` to see already-staged changes
- `git log --oneline -5` to see recent commit message style

### 2. Analyze Changes

Review all changes (staged + unstaged) and determine:
- What was changed (new feature, bug fix, refactor, config update, etc.)
- Which files are relevant to commit
- Whether any files should be excluded (e.g., `.env`, credentials, large binaries)

If there are no changes to commit, inform the user and stop.

### 3. Draft Commit Message

Follow the project's commit message conventions observed from `git log`:
- Use lowercase, concise messages
- Focus on the "what" and "why", not the "how"
- Use imperative mood (e.g., "add feature" not "added feature")
- Keep the first line under 72 characters
- **IMPORTANT**: Do NOT include "Co-Authored-By" or any AI attribution lines (project rule from CLAUDE.md)

### 4. Stage and Commit

- Stage relevant files by name (prefer specific files over `git add -A`)
- Do not stage files that contain secrets or credentials
- Create the commit using a HEREDOC for proper formatting:

```bash
git commit -m "$(cat <<'EOF'
commit message here
EOF
)"
```

### 5. Verify

Run `git status` after committing to confirm success.

## Arguments

The user may optionally provide:
- **A message hint**: `/commit update spot data` - use this as guidance for the commit message
- **Specific files**: `/commit src/main/resources/spots.json` - only stage and commit these files
- **`--amend`**: `/commit --amend` - amend the previous commit instead of creating a new one

If no arguments are provided, commit all current changes with an auto-generated message.

## Examples

```bash
# Auto-commit all changes
/commit

# Commit with a message hint
/commit fix forecast parsing for IFS model

# Commit specific files
/commit src/main/resources/spots.json

# Amend the last commit
/commit --amend
```

## Notes

- Never commit files that likely contain secrets (.env, credentials.json, API keys)
- Warn the user if uncommitted changes mix unrelated concerns (suggest splitting)
- If pre-commit hooks fail, fix the issue and create a NEW commit (don't amend)
- Always verify the commit succeeded with `git status`