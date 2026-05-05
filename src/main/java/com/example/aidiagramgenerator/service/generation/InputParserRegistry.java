package com.example.aidiagramgenerator.service.generation;

import com.example.aidiagramgenerator.enums.InputType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Auto-discovers all {@link InputParser} beans and provides lookup by {@link InputSource}.
 */
@Component
public class InputParserRegistry {

    private static final Logger logger = LoggerFactory.getLogger(InputParserRegistry.class);

    private final Map<InputType, InputParser> parsers = new EnumMap<>(InputType.class);

    public InputParserRegistry(List<InputParser> parserBeans) {
        for (InputParser parser : parserBeans) {
            parsers.put(parser.supports(), parser);
            logger.info("Registered InputParser for {}: {}", parser.supports(), parser.getClass().getSimpleName());
        }
    }

    /**
     * Get the parser for the given input source.
     *
     * @throws IllegalArgumentException if no parser is registered for the source
     */
    public InputParser getParser(InputType inputType) {
        InputParser parser = parsers.get(inputType);
        if (parser == null) {
            throw new IllegalArgumentException("No InputParser registered for type: " + inputType);
        }
        return parser;
    }
}
