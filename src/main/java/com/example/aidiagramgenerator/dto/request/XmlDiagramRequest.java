package com.example.aidiagramgenerator.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for generating diagrams from XML
 */
public class XmlDiagramRequest {

    @NotBlank(message = "XML content cannot be blank")
    @Size(min = 10, max = 50000, message = "XML must be between 10 and 50000 characters")
    private String xml;

    public XmlDiagramRequest() {
    }

    public XmlDiagramRequest(String xml) {
        this.xml = xml;
    }

    public String getXml() {
        return xml;
    }

    public void setXml(String xml) {
        this.xml = xml;
    }

    @Override
    public String toString() {
        return "XmlDiagramRequest{" +
                "xml='" + xml.substring(0, Math.min(xml.length(), 100)) + "...'" +
                '}';
    }
}
