package pro.toparvion.util.heaptalk;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * @author Vladimir Plizga
 */
public interface AiService {

    @SystemMessage(fromResource = "system-message.template")
    @UserMessage(fromResource = "user-message.template")
    String composeSql(@V("ddl") String ddl, @V("question") String question);
}
