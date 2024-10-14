package pro.toparvion.util.heaptalk;

import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;

/**
 * @author Vladimir Plizga
 */
public class AiServiceProvider {

    private static final String PROXY_API_BASE_URL = "https://api.proxyapi.ru/openai/v1";
    
    public static AiService provide(String apiKey, boolean useProxyApi, boolean verbose) {
        OpenAiChatModel.OpenAiChatModelBuilder modelBuilder = OpenAiChatModel.builder();
        if (useProxyApi) {
            modelBuilder.baseUrl(PROXY_API_BASE_URL);
        }
        if (verbose) {
            modelBuilder                
                    .logRequests(true)
                    .logResponses(true);
        }
        ChatLanguageModel model = modelBuilder
                .apiKey(apiKey)
                .modelName(GPT_4_O_MINI)
                .maxRetries(1)
                .temperature(0.5)
                .build();

        return AiServices.create(AiService.class, model);
    }

}
