package net.shasankp000.ChatUtils;

import com.google.gson.JsonSyntaxException;
import io.github.amithkoujalgi.ollama4j.core.OllamaAPI;
import io.github.amithkoujalgi.ollama4j.core.exceptions.OllamaBaseException;
import io.github.amithkoujalgi.ollama4j.core.models.chat.OllamaChatMessageRole;
import io.github.amithkoujalgi.ollama4j.core.models.chat.OllamaChatRequestBuilder;
import io.github.amithkoujalgi.ollama4j.core.models.chat.OllamaChatRequestModel;
import io.github.amithkoujalgi.ollama4j.core.models.chat.OllamaChatResult;
import io.github.amithkoujalgi.ollama4j.core.types.OllamaModelType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class InputIntentConversationClassifier {
    private static final Logger logger = LoggerFactory.getLogger("ai-player");
    private static final String host = "http://localhost:11434/";
    private static final OllamaAPI ollamaAPI = new OllamaAPI(host);

    private static String buildPrompt() {
        return """
                You are a first-principles reasoning intention classifier tool that takes a question/user prompt from a Minecraft player and finds the intention of the question/prompt. Here are some example prompts that you may receive:
                
                1. Hi there!
                2. Hello!
                3. So, how have you been?
                4. What a marvelous day!
                5. The sky looks beautiful?
                6. Greetings!
                
                YOUR TASK is to classify the user's intention based on their prompt. You will not respond directly to the user prompt, only classify the intention. You must respond with only one of the following tags:
                
                - GENERAL_CONVERSATION: For casual greetings, remarks, or any small talk that does not involve a request or query.
                - ASK_INFORMATION: For prompts that ask for information, typically starting with words like 'what,' 'how,' 'where,' 'when,' etc.
                - REQUEST_ACTION: For commands or requests asking the bot to perform an action, usually starting with verbs like 'go,' 'do,' 'build,' 'craft,' etc.
                - UNSPECIFIED: For prompts that lack sufficient context or are nonsensical.
                
                Examples:
                
                **GENERAL_CONVERSATION:**
                - Hi Steve!
                - The weather is nice today.
                - I built a house earlier.
                
                **ASK_INFORMATION:**
                - Did you find any diamonds?
                - How do I craft a shield?
                - Where are the closest villagers?
                
                **REQUEST_ACTION:**
                - Go to coordinates 10 -60 11.
                - Please craft a set of iron armor.
                - Build a shelter before nightfall.
                
                **UNSPECIFIED:**
                - ?
                - Huh?
                - Bananas
                
                However do note that sometimes what may appear to be as something, is usually not what you expect it to be.
                
                For example, sentences starting with can, could, please and other verbs and ending with a question-mark ? may appear to be of the intention ASK_INFORMATION.
                
                However you must analyze the entire sentence.
                
                For example on the context of block detection, the user may say "Please detect if there is a block in front of you." This may look like ASK_INFORMATION, but in reality it is of type REQUEST_ACTION.
                
                The fact that the intention's name is REQUEST_ACTION means that you can expect words like Please, Can, Could, Would, anything which prompts a question, even ends with a question mark, to be sometimes of type REQUEST_ACTION.
                
                So you must analyze the entire sentence very carefully before answering.
                
                ONLY respond with the intention tags (GENERAL_CONVERSATION, ASK_INFORMATION, REQUEST_ACTION, UNSPECIFIED).""";

    }

    public static InputIntentConversationClassifier.Intent getConversationIntent(String userPrompt) {

        OllamaChatRequestBuilder builder = OllamaChatRequestBuilder.getInstance(OllamaModelType.GEMMA2);

        String systemPrompt = buildPrompt();
        String response;

        InputIntentConversationClassifier.Intent intent = InputIntentConversationClassifier.Intent.UNSPECIFIED; // unspecified intention by default.

        try {


            OllamaChatRequestModel requestModel = builder
                    .withMessage(OllamaChatMessageRole.SYSTEM, systemPrompt)
                    .withMessage(OllamaChatMessageRole.USER, userPrompt)
                    .build();

            OllamaChatResult chatResult = ollamaAPI.chat(requestModel);
            response = chatResult.getResponse();
            System.out.println(response);

            if (response.equalsIgnoreCase("GENERAL_CONVERSATION") || response.contains("GENERAL_CONVERSATION")) {

                intent = Intent.GENERAL_CONVERSATION;
            }
            else if (response.equalsIgnoreCase("REQUEST_ACTION") || response.contains("REQUEST_ACTION")) {

                intent = Intent.REQUEST_ACTION;
            }

            else if (response.equalsIgnoreCase("ASK_INFORMATION") || response.contains("ASK_INFORMATION")) {

                intent = Intent.ASK_INFORMATION;
            }


        } catch (OllamaBaseException | IOException | InterruptedException | JsonSyntaxException e) {
            logger.error("{}", (Object) e.getStackTrace());
        }

        return intent;

    }

    public enum Intent {
        REQUEST_ACTION,
        ASK_INFORMATION,
        GENERAL_CONVERSATION,
        UNSPECIFIED
    }


}
