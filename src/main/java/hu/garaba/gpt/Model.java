package hu.garaba.gpt;

public enum Model {
    GPT4("gpt-4-1106-preview"),
    GPT4_Vision("gpt-4-vision-preview"),
    GPT3_TURBO("gpt-3.5-turbo-1106")
    ;

    public final String modelName;
    Model(String modelName) {
        this.modelName = modelName;
    }
}
