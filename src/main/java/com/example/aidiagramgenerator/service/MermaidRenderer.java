package com.example.aidiagramgenerator.service;

/**
 * Converts Mermaid diagram code into a rendered format (e.g. SVG).
 *
 * <p>To plug in a real renderer (e.g. Mermaid CLI, Kroki API, Puppeteer),
 * create a new {@code @Service @Primary} implementation of this interface.</p>
 */
public interface MermaidRenderer {

    /**
     * Render the given Mermaid code to SVG.
     *
     * @param mermaidCode valid Mermaid diagram markup
     * @return SVG document as a string
     */
    String renderToSvg(String mermaidCode);
}
