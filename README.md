# AI Diagram Generator

[![Java](https://img.shields.io/badge/Java-21%2B-orange)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.2-brightgreen)](https://spring.io/projects/spring-boot)
[![Version](https://img.shields.io/badge/version-3.0.0-blue)](https://github.com/marko-todorovski/marko-todorovski/releases/tag/v3.0.0)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)

> **Capstone / Diploma Project**: Automatic Generation of Software Engineering Diagrams from Natural Language, XML, and Repository Analysis

A full-stack Spring Boot application that generates, edits, versions, shares, and collaborates on software engineering diagrams (Mermaid / PlantUML / Draw.io formats). Includes session-based authentication, a project dashboard, an in-browser diagram editor, AI-assisted editing, public sharing, team workspaces, and automated repository analysis.

## Features

- **Diagram generation** from natural language, XML, and repository URLs (Class, Sequence, ER, Architecture, C4 Context, and more)
- **Authentication & security**: session-based auth with Spring Security
- **Project dashboard & editor**: create, edit, and manage diagrams in-browser with version history
- **AI-assisted editing**: refine existing diagrams via natural-language instructions
- **Public sharing**: generate secure, shareable public links for diagrams
- **Team workspaces**: project membership, roles, and email invitations
- **Repository analysis**: import a GitHub repo (or ZIP), detect languages, and scan structure as a foundation for diagram generation
- **Multiple export formats**: Mermaid, PlantUML, Draw.io
- **Human evaluation framework**: clarity, correctness, and usefulness scoring per diagram
- **Database persistence** with Flyway migrations (H2 for dev, PostgreSQL for production)
- **API documentation** via Swagger UI

## Quick Start

### Prerequisites

- Java 21+
- Maven 3.6+ (or use the bundled `./mvnw`)
- (Optional) PostgreSQL for production

### Run

```bash
# Development mode (H2 in-memory database)
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Production mode (PostgreSQL required)
./mvnw spring-boot:run
```

### Access Points

| Service | URL |
|---|---|
| **App** | http://localhost:8080/ |
| **Swagger UI** | http://localhost:8080/swagger-ui.html |
| **API Docs** | http://localhost:8080/api-docs |
| **H2 Console** | http://localhost:8080/h2-console (dev only) |

## Architecture

Layered Spring Boot backend (Controller → Service → Repository → Database) with a modular JSX-based static frontend, no build step required.

```
src/main/java/com/example/aidiagramgenerator/
├── controller/    # REST endpoints: diagrams, auth, projects, workspaces,
│                  #   invitations, sharing, versions, repositories, AI assistant
├── service/       # Business logic, incl. LLM integration points
├── domain/        # JPA entities (Diagram, Project, ProjectMember, Repository, ...)
├── dto/           # Request/response DTOs
├── repository/    # Spring Data JPA repositories
└── exception/     # Centralized exception handling

src/main/resources/
├── db/migration/  # Flyway migrations (h2/ and postgresql/)
└── static/js/     # Modular frontend (auth, editor, projects, repositories,
                   #   collaboration, sharing, ai-assistant, routing)
```

## Project Timeline

| Version | Milestone |
|---|---|
| v1.0.0 | Diagram persistence, generation from text/XML/URL |
| v2.0.0 | Session authentication and security foundation |
| v3.0.0 | Frontend dashboard, editor, version history, AI-assisted editing, public sharing, team workspaces, repository analysis |

See `git log --oneline --decorate` for the full commit history.

## Testing

```bash
./mvnw test
```

## License

MIT
