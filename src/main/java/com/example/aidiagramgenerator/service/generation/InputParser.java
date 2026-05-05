package com.example.aidiagramgenerator.service.generation;

import com.example.aidiagramgenerator.enums.InputType;
import com.example.aidiagramgenerator.service.generation.model.ParsedInput;

/**
 * Strategy interface for parsing raw input into a structured {@link ParsedInput}.
 *
 * <p>Each input source (TEXT, XML, URL) has its own parser implementation that
 * extracts entities, relationships, keywords, and metadata from the raw content.</p>
 *
 * <p><b>Extension point:</b> To support a new input source, implement this interface
 * and register the bean — the framework will auto-discover it.</p>
 */
public interface InputParser {

    /**
     * @return the {@link InputType} this parser handles.
     */
    InputType supports();

    /**
     * Parse the raw content into a structured {@link ParsedInput}.
     *
     * @param rawContent the raw input string (text, XML, URL, etc.)
     * @return structured parsed input
     */
    ParsedInput parse(String rawContent);
}
