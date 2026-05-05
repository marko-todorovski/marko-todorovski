package com.example.aidiagramgenerator.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request DTO for generating diagrams from URLs (e.g., GitHub repos)
 */
public class UrlDiagramRequest {

    @NotBlank(message = "URL cannot be blank")
    @Pattern(regexp = "^https?://.*", message = "URL must be a valid HTTP or HTTPS URL")
    private String url;

    public UrlDiagramRequest() {
    }

    public UrlDiagramRequest(String url) {
        this.url = url;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    @Override
    public String toString() {
        return "UrlDiagramRequest{" +
                "url='" + url + '\'' +
                '}';
    }
}
