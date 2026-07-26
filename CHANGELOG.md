# Changelog

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
