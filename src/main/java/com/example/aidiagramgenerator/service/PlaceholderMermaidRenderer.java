package com.example.aidiagramgenerator.service;

import org.springframework.stereotype.Service;

/**
 * Placeholder Mermaid-to-SVG renderer that returns a static SVG
 * containing the raw Mermaid code as text.
 *
 * <p>Replace with a real implementation (e.g. Mermaid CLI, Kroki, Puppeteer)
 * by creating a {@code @Service @Primary} bean implementing {@link MermaidRenderer}.</p>
 */
@Service
public class PlaceholderMermaidRenderer implements MermaidRenderer {

    @Override
    public String renderToSvg(String mermaidCode) {
        String escaped = mermaidCode
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");

        // Split lines for multi-line <text> display
        String[] lines = escaped.split("\n");
        StringBuilder textElements = new StringBuilder();
        int y = 20;
        for (String line : lines) {
            textElements.append(String.format(
                    "    <text x=\"20\" y=\"%d\" font-family=\"monospace\" font-size=\"14\" fill=\"#e2e8f0\">%s</text>\n",
                    y, line));
            y += 20;
        }

        int height = Math.max(y + 20, 120);

        return String.format("""
                <?xml version="1.0" encoding="UTF-8"?>
                <svg xmlns="http://www.w3.org/2000/svg" width="600" height="%d" viewBox="0 0 600 %d">
                  <rect width="600" height="%d" rx="8" fill="#1e293b"/>

                %s</svg>
                """, height, height, height, textElements.toString()).strip();
    }
}
