package com.example.aidiagramgenerator.service.generation.generator;

import com.example.aidiagramgenerator.enums.DiagramType;
import com.example.aidiagramgenerator.service.generation.DiagramGenerator;
import com.example.aidiagramgenerator.service.generation.model.ParsedInput;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates PlantUML microservices diagrams using {@code rectangle} blocks and {@code -->} arrows.
 *
 * <p>Each service is rendered as a labelled rectangle:</p>
 * <pre>
 * {@code
 * @startuml
 * rectangle "[API Gateway]" as APIGateway
 * rectangle "[Auth Service]" as AuthService
 * rectangle "[Order Service]" as OrderService
 *
 * APIGateway --> AuthService : authenticate
 * APIGateway --> OrderService : route
 * @enduml
 * }
 * </pre>
 */
@Component
public class MicroservicesDiagramGenerator implements DiagramGenerator {

    @Override
    public DiagramType supports() {
        return DiagramType.MICROSERVICES;
    }

    @Override
    public String generate(ParsedInput parsedInput) {
        List<String> entities    = parsedInput.getEntities();
        List<String> rawRels     = parsedInput.getRelationships();

        List<String> services    = resolveServices(entities);
        StringBuilder sb         = new StringBuilder("@startuml\n");

        if (services.isEmpty()) {
            // Default microservices scaffold
            sb.append("rectangle \"[API Gateway]\" as APIGateway\n");
            sb.append("rectangle \"[Auth Service]\" as AuthService\n");
            sb.append("rectangle \"[Order Service]\" as OrderService\n");
            sb.append("rectangle \"[User Service]\" as UserService\n");
            sb.append("\n");
            sb.append("APIGateway --> AuthService : authenticate\n");
            sb.append("APIGateway --> OrderService : route\n");
            sb.append("OrderService --> UserService : user info\n");
        } else {
            // Declare each entity as a rectangle with [Label] style
            for (String[] pair : toAliasedServices(services)) {
                sb.append("rectangle \"[").append(pair[0]).append("]\" as ").append(pair[1]).append("\n");
            }
            sb.append("\n");
            // Render relationships or auto-chain from gateway to services
            if (rawRels != null && !rawRels.isEmpty()) {
                for (String rel : rawRels) {
                    sb.append(rel.replace("->", "-->")).append("\n");
                }
            } else {
                List<String[]> aliased = toAliasedServices(services);
                // First service acts as gateway; connect it to each subsequent service
                for (int i = 1; i < aliased.size(); i++) {
                    sb.append(aliased.get(0)[1]).append(" --> ").append(aliased.get(i)[1])
                      .append(" : route\n");
                }
            }
        }

        sb.append("@enduml");
        return sb.toString();
    }

    /**
     * Converts raw entity strings into sanitised service display names.
     */
    private List<String> resolveServices(List<String> entities) {
        if (entities == null) return List.of();
        List<String> result = new ArrayList<>(entities.size());
        for (String e : entities) {
            String trimmed = e.trim();
            if (!trimmed.isBlank()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    /**
     * Returns {@code [displayName, alias]} pairs for each service.
     * Alias is a PascalCase identifier safe for PlantUML.
     */
    private List<String[]> toAliasedServices(List<String> services) {
        List<String[]> result = new ArrayList<>(services.size());
        for (String svc : services) {
            String alias = svc.replaceAll("[^a-zA-Z0-9]", "_");
            result.add(new String[]{svc, alias});
        }
        return result;
    }
}
