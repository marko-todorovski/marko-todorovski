package com.example.aidiagramgenerator.service.generation.parser;

import com.example.aidiagramgenerator.enums.InputType;
import com.example.aidiagramgenerator.service.generation.InputParser;
import com.example.aidiagramgenerator.service.generation.model.ParsedInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses XML input into a {@link ParsedInput}.
 *
 * <p>Uses {@link DocumentBuilderFactory} to parse the XML document and extract:
 * <ul>
 *   <li>Element names as entities</li>
 *   <li>Attributes as metadata</li>
 *   <li>Parent-child relationships as entity relationships</li>
 * </ul>
 *
 * <p>Falls back to regex-based parsing if XML parsing fails.</p>
 */
@Component
public class XmlInputParser implements InputParser {

    private static final Logger logger = LoggerFactory.getLogger(XmlInputParser.class);

    /** Pattern to extract XML element names (fallback). */
    private static final Pattern ELEMENT_PATTERN = Pattern.compile("<(\\w+)[\\s/>]");

    /** Pattern to extract name attributes (fallback). */
    private static final Pattern NAME_ATTR_PATTERN =
            Pattern.compile("<(\\w+)\\s+[^>]*name=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);

    /** Pattern to detect parent-child nesting (fallback). */
    private static final Pattern NESTING_PATTERN =
            Pattern.compile("<(\\w+)[^/]*>\\s*<(\\w+)", Pattern.DOTALL);

    @Override
    public InputType supports() {
        return InputType.XML;
    }

    @Override
    public ParsedInput parse(String rawContent) {
        logger.debug("Parsing XML input (length={})", rawContent.length());

        ParsedInput parsed = new ParsedInput(rawContent, InputType.XML);

        try {
            // Use DocumentBuilderFactory for proper XML parsing
            parseWithDocumentBuilder(rawContent, parsed);
            parsed.addMetadata("parseMethod", "dom");
        } catch (Exception e) {
            logger.warn("DOM parsing failed, falling back to regex: {}", e.getMessage());
            // Fallback to regex-based parsing
            parseWithRegex(rawContent, parsed);
            parsed.addMetadata("parseMethod", "regex");
        }

        parsed.addMetadata("format", "xml");
        parsed.addMetadata("charCount", String.valueOf(rawContent.length()));

        logger.debug("XML parsing result: {}", parsed);
        return parsed;
    }

    /**
     * Parses XML using DocumentBuilderFactory and extracts diagram model information.
     */
    private void parseWithDocumentBuilder(String xml, ParsedInput parsed) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        // Disable external entities for security
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        
        DocumentBuilder builder = factory.newDocumentBuilder();
        
        // Use StringReader to parse the XML string
        StringReader reader = new StringReader(xml);
        InputSource inputSource = new InputSource(reader);
        Document document = builder.parse(inputSource);
        
        document.getDocumentElement().normalize();
        
        // Extract root element
        Element root = document.getDocumentElement();
        parsed.addMetadata("rootElement", root.getTagName());
        
        // Set to track extracted entities to avoid duplicates
        Set<String> extractedEntities = new HashSet<>();
        
        // Recursively extract nodes and relationships
        extractNodesRecursively(root, null, parsed, extractedEntities);
        
        // Extract attributes from all elements
        extractAttributes(root, parsed);
        
        logger.info("Successfully parsed XML using DocumentBuilder. Entities: {}, Relationships: {}",
                parsed.getEntities().size(), parsed.getRelationships().size());
    }

    /**
     * Recursively extracts nodes from the XML document.
     */
    private void extractNodesRecursively(Node node, String parentName, ParsedInput parsed, Set<String> extracted) {
        if (node.getNodeType() != Node.ELEMENT_NODE) {
            return;
        }
        
        String nodeName = node.getNodeName();
        
        // Skip structural elements
        if (!isStructuralElement(nodeName) && !extracted.contains(nodeName)) {
            parsed.addEntity(nodeName);
            extracted.add(nodeName);
        }
        
        // Extract 'name' attribute if present
        if (node instanceof Element element) {
            String nameAttr = element.getAttribute("name");
            if (nameAttr != null && !nameAttr.isEmpty() && !extracted.contains(nameAttr)) {
                parsed.addEntity(nameAttr);
                extracted.add(nameAttr);
                parsed.addMetadata("element." + nameAttr, nodeName);
            }
            
            // Extract 'id' attribute for entity identification
            String idAttr = element.getAttribute("id");
            if (idAttr != null && !idAttr.isEmpty()) {
                parsed.addMetadata("id." + nodeName, idAttr);
            }
        }
        
        // Create parent-child relationship
        if (parentName != null && !parentName.equals(nodeName) && 
            !isStructuralElement(parentName) && !isStructuralElement(nodeName)) {
            String relationship = parentName + " -> " + nodeName;
            if (!parsed.getRelationships().contains(relationship)) {
                parsed.addRelationship(relationship);
            }
        }
        
        // Process child nodes
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            extractNodesRecursively(children.item(i), nodeName, parsed, extracted);
        }
    }

    /**
     * Extracts all attributes from elements and adds them as metadata.
     */
    private void extractAttributes(Node node, ParsedInput parsed) {
        if (node.getNodeType() == Node.ELEMENT_NODE) {
            NamedNodeMap attributes = node.getAttributes();
            if (attributes != null) {
                for (int i = 0; i < attributes.getLength(); i++) {
                    Node attr = attributes.item(i);
                    String key = "attr." + node.getNodeName() + "." + attr.getNodeName();
                    parsed.addMetadata(key, attr.getNodeValue());
                }
            }
        }
        
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            extractAttributes(children.item(i), parsed);
        }
    }

    /**
     * Fallback regex-based parsing when DOM parsing fails.
     */
    private void parseWithRegex(String xml, ParsedInput parsed) {
        extractElements(xml, parsed);
        extractNamedEntities(xml, parsed);
        extractNestingRelationships(xml, parsed);
    }

    private void extractElements(String xml, ParsedInput parsed) {
        Matcher matcher = ELEMENT_PATTERN.matcher(xml);
        while (matcher.find()) {
            String element = matcher.group(1);
            if (!isStructuralElement(element) && !parsed.getEntities().contains(element)) {
                parsed.addEntity(element);
            }
        }
    }

    private void extractNamedEntities(String xml, ParsedInput parsed) {
        Matcher matcher = NAME_ATTR_PATTERN.matcher(xml);
        while (matcher.find()) {
            String elementType = matcher.group(1);
            String name = matcher.group(2);
            if (!parsed.getEntities().contains(name)) {
                parsed.addEntity(name);
            }
            parsed.addMetadata("element." + name, elementType);
        }
    }

    private void extractNestingRelationships(String xml, ParsedInput parsed) {
        Matcher matcher = NESTING_PATTERN.matcher(xml);
        while (matcher.find()) {
            String parent = matcher.group(1);
            String child = matcher.group(2);
            if (!parent.equals(child)) {
                String rel = parent + " -> " + child;
                if (!parsed.getRelationships().contains(rel)) {
                    parsed.addRelationship(rel);
                }
            }
        }
    }

    private boolean isStructuralElement(String element) {
        return element.equalsIgnoreCase("xml") ||
                element.equalsIgnoreCase("root") ||
                element.equalsIgnoreCase("data") ||
                element.equalsIgnoreCase("item") ||
                element.equalsIgnoreCase("list");
    }
}
