package ru.girchev.aibot.ai.springai.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import ru.girchev.aibot.common.ai.ModelType;

import java.util.List;

@Getter
@Setter
public class SpringAIModelConfig {
    
    @NotBlank(message = "Name of model cannot be blank")
    private String name;
    
    @NotEmpty(message = "List of capabilities cannot be empty")
    private List<ModelType> capabilities;
    
    @NotNull(message = "Provider type is required")
    private ProviderType providerType;
    
    @NotNull(message = "Priority is required")
    @Min(value = 1, message = "Priority must be >= 1")
    private Integer priority;
    
    public enum ProviderType {
        OLLAMA,
        OPENAI
    }
}

