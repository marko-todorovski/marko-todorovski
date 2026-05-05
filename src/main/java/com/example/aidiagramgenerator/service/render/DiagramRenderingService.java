package com.example.aidiagramgenerator.service.render;

/**
 * Service interface for rendering PlantUML diagrams to image formats.
 * Supports rendering to PNG and SVG formats.
 */
public interface DiagramRenderingService {

    /**
     * Renders the given PlantUML code to PNG format.
     *
     * @param plantUml the PlantUML code to render
     * @return the PNG image as a byte array
     * @throws IllegalArgumentException   if plantUml is null or blank
     * @throws DiagramRenderingException  if rendering fails
     */
    byte[] renderToPng(String plantUml);

    /**
     * Renders the given PlantUML code to SVG format.
     *
     * @param plantUml the PlantUML code to render
     * @return the SVG image as a byte array
     * @throws IllegalArgumentException   if plantUml is null or blank
     * @throws DiagramRenderingException  if rendering fails
     */
    byte[] renderToSvg(String plantUml);
}
