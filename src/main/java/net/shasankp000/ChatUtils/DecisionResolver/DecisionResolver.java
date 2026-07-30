package net.shasankp000.ChatUtils.DecisionResolver;

import io.github.amithkoujalgi.ollama4j.core.OllamaAPI;
import io.github.amithkoujalgi.ollama4j.core.exceptions.OllamaBaseException;
import io.github.amithkoujalgi.ollama4j.core.exceptions.ToolInvocationException;
import io.github.amithkoujalgi.ollama4j.core.models.chat.OllamaChatMessageRole;
import io.github.amithkoujalgi.ollama4j.core.models.chat.OllamaChatRequestBuilder;
import io.github.amithkoujalgi.ollama4j.core.models.chat.OllamaChatRequestModel;
import io.github.amithkoujalgi.ollama4j.core.models.chat.OllamaChatResult;
import net.shasankp000.AIPlayer;
import net.shasankp000.FilingSystem.LLMClientFactory;
import net.shasankp000.ServiceLLMClients.LLMClient;
import net.shasankp000.ServiceLLMClients.LLMServiceHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DecisionResolver {

    // Set to your Ollama server and desired model
    private static final String OLLAMA_HOST = "http://localhost:11434/";
    private static OllamaAPI ollamaAPI = new OllamaAPI(OLLAMA_HOST);
    private static final Pattern THINK_BLOCK = Pattern.compile("<think>([\\s\\S]*?)</think>");
    private static final Logger LOGGER = LoggerFactory.getLogger("DecisionResolver");

    public DecisionResolver() {
        ollamaAPI = new OllamaAPI(OLLAMA_HOST);
        ollamaAPI.setRequestTimeoutSeconds(300);
    }

    /**
     * Compose the LLM prompt with all classifier predictions and confidences.
     */
    private String buildPrompt(
            String playerMessage,
            String bertPred, double bertConf,
            String cartMainPred, double cartMainConf,
            String lidsNetPred, double lidsNetConf
    ) {
        return "You are a final Intent Decision Resolver for a Minecraft AI mod. " +
                "Your task is to read below the outputs and confidences from six intent classifiers and fairly deduce the player's intent out of the three intents: GENERAL_CONVERSATION: Just chatting, ASK_INFORMATION: Requesting information regarding something, REQUEST_ACTION: Requesting an action to be executed." +
                "Decide between: REQUEST_ACTION, ASK_INFORMATION, GENERAL_CONVERSATION. You should output only either the three of these based on your reasoning and the output should be the exact same as the labels, otherwise the output resolver will fail.\n" +
                "- Player message: \"" + playerMessage + "\"\n" +
                "- BERT: " + bertPred + " (" + String.format("%.2f", bertConf) + ")\n" +
                "- Main CART: " + cartMainPred + " (" + String.format("%.2f", cartMainConf) + ")\n" +
                "- LIDSNet: " + lidsNetPred + " (" + String.format("%.2f", lidsNetConf) + ")\n" +
                "\nYour decision: Reply ONLY with one of REQUEST_ACTION, ASK_INFORMATION, GENERAL_CONVERSATION. " +
                "If truly ambiguous, return UNSPECIFIED. No further explanation."+
                "Also, do keep in mind that these classifiers are very experimental, so often they might return outputs which totally goes against your intuition, such as misclassifying a GENERAL_ACTION as REQUEST_ACTION or ASK_INFORMATION, or vice versa. In such instances, DO NOT OVERTHINK, DO NOT TRUST THE CLASSIFIERS and just go with what intent the input best fits according to you and return only the intent, not anything else";
    }

    /**
     * Runs the Ollama LLM as Decision Resolver and returns the intent decision.
     */
    public String resolveIntent(
            String playerMessage,
            String bertPred, double bertConf,
            String cartMainPred, double cartMainConf,
            String lidsNetPred, double lidsNetConf
    ) throws OllamaBaseException, IOException, InterruptedException, ToolInvocationException {

        String prompt = buildPrompt(
                playerMessage,
                bertPred, bertConf,
                cartMainPred, cartMainConf,
                lidsNetPred, lidsNetConf
        );

        String selectedLM = AIPlayer.CONFIG.getSelectedLanguageModel();

        String llmProvider = System.getProperty("aiplayer.llmMode", "ollama");

        String answer = "";

        switch (llmProvider) {
            case "openai", "claude", "grok", "gemini":
                LLMClient llmClient = LLMClientFactory.createClient(llmProvider);
                if (llmClient!=null) {
                    if (llmClient.isReachable()) {
                        answer = llmClient.sendPrompt("Analyze the user prompt thoroughly and answer only as directed in the user prompt. Do not deviate from the expected output pattern as stated in the user prompt.", prompt);
                    }
                    else {
                        LOGGER.error("Error! {} client is not reachable. Please check your internet connection or try again later", llmClient.getProvider());
                    }
                }
                else {
                    LOGGER.error("Error! {} client is null!", llmProvider);
                }
                break;
            case "ollama":
                OllamaChatRequestModel requestModel = OllamaChatRequestBuilder.getInstance(selectedLM)
                        .withMessage(OllamaChatMessageRole.USER, prompt)
                        .build();


                OllamaChatResult response = ollamaAPI.chat(requestModel);

                answer = response.getResponse().trim();

                break;

            default:
                LOGGER.warn("Unsupported provider detected.");
                return "UNSPECIFIED";
        }

        answer = processLLMOutput(answer);

        System.out.println("Answer: " + answer);

        if (answer.contains("REQUEST_ACTION")) return "REQUEST_ACTION";
        if (answer.contains("ASK_INFORMATION")) return "ASK_INFORMATION";
        if (answer.contains("GENERAL_CONVERSATION")) return "GENERAL_CONVERSATION";
        if (answer.contains("UNSPECIFIED")) return "UNSPECIFIED";
        if (answer.contains("No response!")) return "UNSPECIFIED";
        return "";
    }


    public static String processLLMOutput(String fullResponse) {
        Matcher matcher = THINK_BLOCK.matcher(fullResponse);

        if (matcher.find()) {
            String thinking = matcher.group(1).trim();
            String remainder = fullResponse.replace(matcher.group(0), "").trim();

            LOGGER.debug("Thinking part: {}", thinking);

            if (!remainder.isEmpty()) {
                return remainder;
            }
            else {
                return "No response!";
            }
        } else {
            if (fullResponse != null || !fullResponse.isEmpty()) {
                return fullResponse;
            }
            else {
                return "No response!";
            }
        }
    }

}
