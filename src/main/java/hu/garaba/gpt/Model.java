package hu.garaba.gpt;

public enum Model {
    GPT4("gpt-4o-2024-05-13", true),
    GPT3_TURBO("gpt-3.5-turbo-0125", true),
    DALL_E_3("dall-e-3", false),
    WHISPER_1("whisper-1", false)
    ;

    public final String modelName;
    public final boolean isConversationModel;
    Model(String modelName, boolean isConversationModel) {
        this.modelName = modelName;
        this.isConversationModel = isConversationModel;
    }
}
