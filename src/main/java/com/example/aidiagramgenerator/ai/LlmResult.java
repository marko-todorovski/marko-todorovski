package com.example.aidiagramgenerator.ai;

/**
 * Represents the outcome of a {@link AiModelService#callLLM} invocation.
 *
 * <p>Encapsulates whether the call succeeded and the content returned,
 * eliminating the need for sentinel string values like "LLM unavailable".
 *
 * <p>Callers should check {@link #isSuccess()} before reading {@link #getContent()}.
 */
public final class LlmResult {

    private final boolean success;
    private final String content;

    private LlmResult(boolean success, String content) {
        this.success = success;
        this.content = content;
    }

    /**
     * Creates a successful result containing the model's response content.
     *
     * @param content the non-null, non-blank content returned by the LLM
     * @return a successful {@link LlmResult}
     */
    public static LlmResult success(String content) {
        return new LlmResult(true, content);
    }

    /**
     * Creates a failure result representing an unavailable or erring LLM.
     *
     * @return a failure {@link LlmResult} with {@code null} content
     */
    public static LlmResult failure() {
        return new LlmResult(false, null);
    }

    /**
     * @return {@code true} if the LLM call succeeded and content is available
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * @return the LLM response content, or {@code null} when {@link #isSuccess()} is {@code false}
     */
    public String getContent() {
        return content;
    }

    @Override
    public String toString() {
        return success ? "LlmResult{success=true, contentLength=" + (content != null ? content.length() : 0) + "}"
                       : "LlmResult{success=false}";
    }
}
