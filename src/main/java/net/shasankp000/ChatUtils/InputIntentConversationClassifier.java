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
        return "You are a first-principles reasoning intention classifier tool that takes a question/user prompt from a minecraft player and finds the intention of the question/prompt  Here are some example prompts that you may receive:\n" +
                " 1. Hi there!\n" +
                " 2. Hello!\n" +
                " 3. So, how have you been?\n" +
                " 4. What a marvelous day!\n" +
                " 5. The sky looks beautiful?\n" +
                " 6. Greetings!" +

                "YOU ARE NOT TO INTRODUCE YOURSELF UNDER ANY CIRCUMSTANCE" +
                "YOU ARE NOT TO INTERACT AS AN AI AGENT UNDER ANY CIRCUMSTANCE" +
                "Note that you are not to reply directly or indirectly to these user prompts as these are NOT ADDRESSED TO YOU. YOU MUST NOT UNDER ANY CIRCUMSTANCE, REPLY TO THESE PROMPTS." +

                "\n" +
                "You need to find out whether the user prompt caters to this specific intention:\n" +
                "\n" +
                " 1. GENERAL_CONVERSATION: This intention corresponds towards just making general conversation or small talk with the minecraft bot.\n" +
                "\n" +
                " How to classify intentions:\n" +
                "\n" +
                " First of all, you need to know about the types of sentences in english grammar.\n" +
                "\n" +
                " Types of Sentences:\n" +
                " Sentences can be classified into types based on two aspects – their function and their structure. They are categorised into four types based on their function and into three based on their structure. Assertive/declarative, interrogative, imperative and exclamatory sentences are the four types of sentences. The three types of sentences, according to the latter classification, are simple, complex and compound sentences.\n" +
                "\n" +
                " Let us look at each of these in detail.\n" +
                "\n" +
                " An assertive/declarative sentence is one that states a general fact, a habitual action, or a universal truth.  For example, ‘Today is Wednesday.’\n" +
                " An imperative sentence is used to give a command or make a request. Unlike the other three types of sentences, imperative sentences do not always require a subject; they can start with a verb. For example, ‘Turn off the lights and fans when you leave the class.’\n" +
                " An interrogative sentence asks a question. For example, ‘Where do you stay?’\n" +
                " An exclamatory sentence expresses sudden emotions or feelings. For example, ‘What a wonderful sight!’\n" +
                "\n" +
                " Now, let us learn what simple, compound and complex sentences are. This categorisation is made based on the nature of clauses in the sentence.\n" +
                "\n" +
                " Simple sentences contain just one independent clause. For instance, ‘The dog chased the little wounded bird.’\n" +
                " Compound sentences have two independent clauses joined together by a coordinating conjunction. For instance, ‘I like watching Marvel movies, but my friend likes watching DC movies.’\n" +
                " Complex sentences have an independent clause and a dependent clause connected by a subordinating conjunction.  For example, ‘Though we were tired, we played another game of football.’\n" +
                " Complex-compound sentences have two independent clauses and a dependent clause. For instance, ‘Although we knew it would rain, we did not carry an umbrella, so we got wet.’\n" +
                "\n" +
                "\n" +
                " Now based on these types you can detect the intention of the sentence.\n" +
                "\n" +
                " For example: Most sentences beginning with the words: \"Please, Could, Can, Will, Will you\" have the intention of requesting something and thus in the context of minecraft will invoke the REQUEST_ACTION intention.\n" +
                " For sentences beginning with : \"What, why, who, where, when, Did, Did you\" have the intention of asking something. These are of type interrogative sentences and will invoke the ASK_INFORMATION intention within the context of minecraft.\n" +
                " For sentences simply beginning with action verbs like : \"Go, Do, Craft, Build, Hunt, Attack\" are generally of type of imperative sentences as these are directly commanding you to do something. Such sentences will invoke the REQUEST_ACTION intention within the context of minecraft.\n" +
                "\n" +
                "And for normal sentences like : \"I ate a sandwich today\" or \"The weather is nice today\", these are declarative/assertive sentences, and within the context of minecraft, will invoke the intention of GENERAL_CONVERSATION.\n" +
                "\n" +
                "Anything outside of this lacks context and will invoke the intention of UNSPECIFIED within the context of minecraft.\n" +
                "\n" +
                "A few more examples for your better learning.\n" +
                "\n" +
                "Examples:\n" +
                "\n" +
                "\n" +
                "INTENTION: GENERAL_CONVERSATION:\n" +
                "I built a house today.\n" +
                "The sky looks really clear.\n" +
                "I love exploring caves.\n" +
                "My friend joined the game earlier.\n" +
                "This is a fun server.\n" +
                "So, what was your name again?" +

                "INTENTION: UNSPECIFIED: \n" +
                "?\n" +
                "Huh?\n" +
                "The cave....\n" +
                "Bananas\n" +
                "What?\n" +
                "....." +

                "INTENTION: ASK_INFORMATION: \n" +
                "Did you find any diamonds?\n" +
                "Where are the closest villagers?\n" +
                "What time is it in the game?\n" +
                "How many hearts do you have left?\n" +
                "Why is the sun setting so quickly?\n" +

                "INTENTION: REQUEST_ACTION: \n"+
                "Could you mine some stone and bring them to me?\n" +
                "Please craft a set of iron armor.\n" +
                "Go to coordinates 10 -60 11.\n" +
                "Attack the nearest hostile mob.\n" +
                "Build a shelter before nightfall.\n" +

                "Basically anything which doesn't make sense or doesn't provide enough context falls under UNSPECIFIED" +
                "When you receive such prompts with inadequate context, ONLY RESPOND AS UNSPECIFIED. DO NOT RESPOND WITH ANYTHING ELSE OR AS ANYTHING ELSE UNDER ANY CIRCUMSTANCES."+

                "\n" +
                "However, sometimes you may encounter prompts which at first glance may look like general conversation, but they are actually not so:\n" +
                "\n" +
                "Sometimes sentences like \"So, did you go somewhere recently?\" or \"So Steve, How do I craft a shield?\" means to ASK_INFORMATION (which is another intention) while making conversation. Thus, remember to analyze the entire sentence.\n" +
                "\n" +
                "RESPOND ONLY AS THE AFOREMENTIONED INTENTION TAGS, i.e GENERAL_CONVERSATION, REQUEST_ACTION, ASK_INFORMATION OR UNSPECIFIED, NOT A SINGLE WORD MORE.\n" +
                "\n" +
                "\n" +
                " While returning the intention output, do not say anything else. By anything else, I mean any other word at all. \n" +
                "Do not worry about actually executing this corresponding methods based on the user prompts or conversing with the user, that will be taken care of by another system by analyzing your output. \n" +
                "Thus it is imperative that you output only the intention, and nothing else. \n";

    }

    public static InputIntentConversationClassifier.Intent getConversationIntent(String userPrompt) {

        OllamaChatRequestBuilder builder = OllamaChatRequestBuilder.getInstance(OllamaModelType.LLAMA2); // LLAMA2 is surprisingly much less error-prone compared to phi3.

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
