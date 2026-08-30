package net.shasankp000.ServiceLLMClients;

/**
 * OpenAI client using the Responses API by default, with automatic fallback to
 * Chat Completions for models that do not support Responses.
 */
public class OpenAIClient implements LLMClient {
    private static final String OPENAI_RESPONSES_URL = "https://api.openai.com/v1/responses";

    private final GenericOpenAIClient delegate;

    public OpenAIClient(String apiKey, String modelName) {
        this.delegate = new GenericOpenAIClient(apiKey, modelName, OPENAI_RESPONSES_URL);
    }

    @Override
    public String sendPrompt(String systemPrompt, String userPrompt) {
        return delegate.sendPrompt(systemPrompt, userPrompt);
    }

    @Override
    public boolean isReachable() {
        return delegate.isReachable();
    }

    @Override
    public String getProvider() {
        return "OpenAI";
    }
}
