package hu.garaba.gpt;

public enum Model {
    O4_MINI("o4-mini", true, true), // Fast reasoning model
    O3("o3", true, true), // Smartest reasoning model
    GPT_4_1_MINI("gpt-4.1-mini", true, true), // Fast model
    GPT_4_1("gpt-4.1", true, true), // Fast model
    GPT_5("gpt-5", true, true), // Fast model
    DALL_E_3("dall-e-3", false, false),
    WHISPER_1("whisper-1", false, false)
    ;

    public static final Model DEFAULT_MODEL = GPT_4_1_MINI;

    public final String modelName;
    public final boolean isConversationModel;
    public final boolean supportsImageInput;

    Model(String modelName, boolean isConversationModel, boolean supportsImageInput) {
        this.modelName = modelName;
        this.isConversationModel = isConversationModel;
        this.supportsImageInput = supportsImageInput;
    }
}
