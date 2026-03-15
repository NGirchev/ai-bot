package io.github.ngirchev.opendaimon.common.ai.command;

import io.github.ngirchev.opendaimon.common.ai.ModelCapabilities;

import java.util.Map;
import java.util.Set;

public record ModelListAICommand(Map<String, String> metadata) implements AICommand {

    @Override
    public Set<ModelCapabilities> modelCapabilities() {
        return Set.of();
    }

    @Override
    public <T extends AICommandOptions> T options() {
        return null;
    }
}
