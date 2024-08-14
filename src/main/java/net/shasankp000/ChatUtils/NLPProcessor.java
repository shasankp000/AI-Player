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

public class NLPProcessor {

    private static final Logger logger = LoggerFactory.getLogger("ai-player");
    private static final String host = "http://localhost:11434/";
    private static final OllamaAPI ollamaAPI = new OllamaAPI(host);

    private static String buildPrompt() {
        return "You are a first-principles reasoning function caller AI agent that takes a question/user prompt from a minecraft player and finds the intention of the question/prompt  Here are some example prompts that you may receive:\n" +
                " 1. Could you check if there is a block in front of you?\n" +
                " 2. Look around for any hostile mobs, and report to me if you find any.\n" +
                " 3. Could you mine some stone and bring them to me?\n" +
                " 4. Craft a set of iron armor.\n" +
                " 5. Did you go somewhere recently?\n" +
                "\n" +
                " These are the following intentions which the prompt may cater to and which you have to figure out:\n" +
                "\n" +
                " 1. REQUEST_ACTION: This intention corresponds towards requesting a minecraft bot to take an action such as going somewhere, exploring, scouting etc.\n" +
                " 2. ASK_INFORMATION: This intention corresponds asking a minecraft bot for information, which could be about the game or anything else.\n" +
                " 3. GENERAL_CONVERSATION: This intention corresponds towards just making general conversation or small talk with the minecraft bot.\n" +
                " 4. UNSPECIFIED: This intention corresponds to a message which lacks enough context for proper understanding.\n" +
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
                "1. REQUEST_ACTION:\n" +
                "Could you mine some stone and bring them to me?\n" +
                "Please craft a set of iron armor.\n" +
                "Go to coordinates 10 -60 11.\n" +
                "Attack the nearest hostile mob.\n" +
                "Build a shelter before nightfall.\n" +
                "\n" +
                "2. ASK_INFORMATION:\n" +
                "Did you find any diamonds?\n" +
                "Where are the closest villagers?\n" +
                "What time is it in the game?\n" +
                "How many hearts do you have left?\n" +
                "Why is the sun setting so quickly?\n" +
                "\n" +
                "3. GENERAL_CONVERSATION:\n" +
                "I built a house today.\n" +
                "The sky looks really clear.\n" +
                "I love exploring caves.\n" +
                "My friend joined the game earlier.\n" +
                "This is a fun server.\n" +
                "\n" +
                "4. UNSPECIFIED:\n" +
                "Incomplete: Can you...\n" +
                "Ambiguous: Do it.\n" +
                "Vague: Make something cool.\n" +
                "Out of context: \"What are we?\n" +
                "General statement with unclear intent: The weather.\n" +
                "\n" +
                "For further ease of classification of input, here are some keywords you can focus on within the prompt.\n" +
                "\n" +
                "Such keywords include:\n" +
                "\n" +
                "         move\n" +
                "         go\n" +
                "         walk\n" +
                "         run\n" +
                "         navigate\n" +
                "         travel\n" +
                "         step\n" +
                "         approach\n" +
                "         advance\n" +
                "         mine\n" +
                "         dig\n" +
                "         excavate\n" +
                "         collect\n" +
                "         gather\n" +
                "         break\n" +
                "         harvest\n" +
                "         attack\n" +
                "         fight\n" +
                "         defend\n" +
                "         slay\n" +
                "         kill\n" +
                "         vanquish\n" +
                "         destroy\n" +
                "         battle\n" +
                "         craft\n" +
                "         create\n" +
                "         make\n" +
                "         build\n" +
                "         forge\n" +
                "         assemble\n" +
                "         trade\n" +
                "         barter\n" +
                "         exchange\n" +
                "         buy\n" +
                "         sell\n" +
                "         explore\n" +
                "         discover\n" +
                "         find\n" +
                "         search\n" +
                "         locate\n" +
                "         scout\n" +
                "         construct\n" +
                "         erect\n" +
                "         place\n" +
                "         set\n" +
                "         farm\n" +
                "         plant\n" +
                "         grow\n" +
                "         cultivate\n" +
                "         use\n" +
                "         utilize\n" +
                "         activate\n" +
                "         employ\n" +
                "         operate\n" +
                "         handle\n" +
                "         check\n" +
                "         search\n" +
                "\n" +
                "         Some of the above keywords are synonyms of each other. (e.g check -> search, kill -> vanquish, gather->collect)\n" +
                "\n" +
                "         So you must be on the lookout for the synonyms of such keywords as well.\n" +
                "\n" +
                "         These keywords fall under the category of action-verbs. Since your purpose is to design the output that will call a function, which will trigger an action, you need to know what a verb is and what action-verbs are to further your ease in selecting the appropriate function.\n" +
                "\n" +
                "         A verb is a a word used to describe an action, state, or occurrence, and forming the main part of the predicate of a sentence, such as hear, become, happen.\n" +
                "\n" +
                "         An action verb (also called a dynamic verb) describes the action that the subject of the sentence performs (e.g., “I  run”).\n" +
                "\n" +
                "         Example of action verbs:\n" +
                "\n" +
                "         We \"traveled\" to Spain last summer.\n" +
                "         My grandfather \"walks\" with a stick.\n" +
                "\n" +
                "         The train \"arrived\" on time.\n" +
                "\n" +
                "         I \"ate\" a sandwich for lunch.\n" +
                "\n" +
                "         All the verbs within quotations cite actions that were caused/triggered.\n" +
                "\n" +
                "         So when you are supplied with a prompt that contain the *keywords* which is provided earlier, know that these are actions which correspond to a particular tool within the provided tools.\n" +
                "\n" +
                "However detecting such keyword and immediately classifying it as an action is incorrect.\n" +
                "\n" +
                "Sometimes sentences like \"So, did you go somewhere recently?\" means to ASK_INFORMATION while making conversation. Remember to analyze the entire sentence.\n" +
                "\n" +
                "RESPOND ONLY AS THE AFOREMENTIONED INTENTION TAGS, i.e REQUEST_ACTION, ASK_INFORMATION, GENERAL_CONVERSATION and UNSPECIFIED, NOT A SINGLE WORD MORE.\n" +
                "\n" +
                "\n" +
                " While returning the intention output, do not say anything else. By anything else, I mean any other word at all. \n" +
                "Do not worry about actually executing this corresponding methods based on the user prompts or conversing with the user, that will be taken care of by another system by analyzing your output. \n" +
                "Thus it is imperative that you output only the intention, and nothing else. \n";

    }

    public static Intent getIntention(String userPrompt) {

        OllamaChatRequestBuilder builder = OllamaChatRequestBuilder.getInstance(OllamaModelType.PHI3); // LLAMA2 is surprisingly much less error-prone compared to phi3.

        String systemPrompt = buildPrompt();
        String response;

        Intent intent = Intent.UNSPECIFIED; // unspecified intention by default.

        try {


            OllamaChatRequestModel requestModel = builder
                    .withMessage(OllamaChatMessageRole.SYSTEM, systemPrompt)
                    .withMessage(OllamaChatMessageRole.USER, userPrompt)
                    .build();

            OllamaChatResult chatResult = ollamaAPI.chat(requestModel);
            response = chatResult.getResponse();

            if (response.equalsIgnoreCase("REQUEST_ACTION") || response.contains("REQUEST_ACTION")) {

                intent = Intent.REQUEST_ACTION;
            }

            else if (response.equalsIgnoreCase("ASK_INFORMATION") || response.contains("ASK_INFORMATION")) {

                intent = Intent.ASK_INFORMATION;
            }

            else if (response.equalsIgnoreCase("GENERAL_CONVERSATION") || response.contains("GENERAL_CONVERSATION")) {

                intent = Intent.GENERAL_CONVERSATION;
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
