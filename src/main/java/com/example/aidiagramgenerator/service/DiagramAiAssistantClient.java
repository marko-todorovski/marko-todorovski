package com.example.aidiagramgenerator.service;

public interface DiagramAiAssistantClient {

    String getModelName();

    String generateStructuredResponse(String prompt);
}
