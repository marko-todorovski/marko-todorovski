# Changelog

## v3.0.3

**🚀 Live deployment: [https://ai-diagrams-app.onrender.com/](https://ai-diagrams-app.onrender.com/)**

The app is now deployed to production (Render: Docker web service + managed PostgreSQL). This release covers the deployment itself plus fixes and verification found while smoke-testing the live instance.

Added:
- Production deployment to Render (Docker build, managed PostgreSQL, Flyway migrations run automatically on startup)

Fixed:
- PlantUML diagrams were rendering as an error image instead of the actual diagram in production because the Graphviz `dot` binary was missing from the runtime container. Added `graphviz` to the Docker runtime image and set `GRAPHVIZ_DOT` so PlantUML can find it.
- Verified CHECK-constraint bugs from v3.0.2 stay fixed against a real (non-H2) PostgreSQL instance.

Verified via full smoke test against the live deployment:
- Registration, login, project creation, diagram generation (rule-based + NLP fallback), saving, in-browser editor, version history, public share links, team collaboration/invitations, and GitHub repository analysis all work end-to-end in production.

Known limitation:
- The AI Assistant's real-AI features (Explain / Suggest / Modify via OpenAI) are **not functional on the live deployment** because `OPENAI_API_KEY` is not configured on the Render service. Diagram generation itself is unaffected — it degrades gracefully to the rule-based/NLP heuristics engine. This is an intentional, accepted trade-off for this deployment, not a bug.

## v3.0.1

Fixed:
- Fixed classification suggestion "Proceed" button (mapTypeToEnum ReferenceError).

## v3.0.0

Added:
- AI-assisted diagram generation (rule-based + OpenAI/Ollama providers)
- Interactive diagram editor with PlantUML source and live preview
- Diagram version history with save/restore
- AI Assistant: explain diagram, suggest improvements, modify with AI (proposal-based edits)
- Secure public diagram sharing via expiring share links
- Team workspaces: project membership, roles (Owner/Editor/Viewer), invitations
- Repository analysis: import a GitHub repository and generate diagrams from its structure
- Session-based authentication and authorization foundation

## v1.0.0

Added:
- Initial release: diagram persistence and versioning services
- Flyway schema migrations
- Ownership-aware persistence repositories
- Session authentication and security foundation
- Authenticated workspace REST APIs
- Frontend authentication and project dashboard
