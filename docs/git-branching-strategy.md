# Git Branching Strategy

## Branching Model: Solo DevFlow

A lightweight adaptation of Git Flow for a single developer on a Spring Boot microservices project.

### Branches

| Branch      | Purpose                                                    | Base     |
|-------------|------------------------------------------------------------|----------|
| `main`      | Production-ready, tagged releases                          | —        |
| `develop`   | Integration of completed features, CI runs here           | `main`   |
| `feature/*` | New features, tutorials, or services                       | `develop`|
| `hotfix/*`  | Urgent fixes to production                                 | `main`   |

### Workflow

1. Start a new feature/tutorial module:
   ```bash
   git checkout -b feature/patient-service-crud develop
   ```

2. Work, commit, push the feature branch:
   ```bash
   git add .
   git commit -m "feat: add patient CRUD endpoints"
   git push -u origin feature/patient-service-crud
   ```

3. Merge to `develop` when complete:
   ```bash
   git checkout develop
   git pull origin develop
   git merge --no-ff feature/patient-service-crud
   git push origin develop
   ```

4. Merge to `main` when ready for a release:
   ```bash
   git checkout main
   git pull origin main
   git merge --no-ff develop
   git tag -a v1.0.0 -m "Release v1.0.0"
   git push origin main --tags
   ```

### Commit Message Convention (Conventional Commits)

```
feat:     new feature for the user (e.g., new API endpoint)
fix:      bug fix for the user
docs:     changes to documentation (README, ADRs, etc.)
style:    formatting, missing semicolons, etc. (no logic change)
refactor: code change that neither fixes a bug nor adds a feature
perf:     code change that improves performance
test:     adding or updating tests
chore:    build process, dependencies, CI/CD changes
```

### Release Tagging

Use [Semantic Versioning](https://semver.org/):
- `v0.1.0` — Initial patient service
- `v0.2.0` — Add Docker support
- `v1.0.0` — Deployed to AWS (production)

### Why This Works for This Project

- **Tutorial modules** map naturally to `feature/*` branches.
- **Multi-service setup** stays sane when merging into `develop`.
- **Tagging releases** gives clean rollback points for AWS deploys.
- **No pull request noise** — merge locally, keep moving fast.
