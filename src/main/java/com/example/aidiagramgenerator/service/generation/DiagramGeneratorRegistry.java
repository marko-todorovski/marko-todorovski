package com.example.aidiagramgenerator.service.generation;

import com.example.aidiagramgenerator.enums.DiagramType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Auto-discovers all {@link DiagramGenerator} beans and provides lookup by {@link DiagramType}.
 *
 * <p><b>Extension point:</b> Simply add a new {@link DiagramGenerator} Spring bean and it will
 * be picked up here automatically — no registration code needed.</p>
 */
@Component
public class DiagramGeneratorRegistry {

    private static final Logger logger = LoggerFactory.getLogger(DiagramGeneratorRegistry.class);

    private final Map<DiagramType, DiagramGenerator> generators = new EnumMap<>(DiagramType.class);

    public DiagramGeneratorRegistry(List<DiagramGenerator> generatorBeans) {
        for (DiagramGenerator gen : generatorBeans) {
            generators.put(gen.supports(), gen);
            logger.info("Registered DiagramGenerator for {}: {}", gen.supports(), gen.getClass().getSimpleName());
        }
    }

    /**
     * Get the generator for the given diagram type.
     *
     * @throws IllegalArgumentException if no generator is registered for the type
     */
    public DiagramGenerator getGenerator(DiagramType type) {
        DiagramGenerator generator = generators.get(type);
        if (generator == null) {
            throw new IllegalArgumentException("No DiagramGenerator registered for type: " + type);
        }
        return generator;
    }

    /**
     * @return all diagram types that have a registered generator.
     */
    public Set<DiagramType> supportedTypes() {
        return generators.keySet();
    }
}
