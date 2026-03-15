package io.github.ngirchev.opendaimon.common.ai.model;

import io.github.ngirchev.opendaimon.common.ai.ModelCapabilities;

import java.util.Set;

public record ModelInfo(String name, Set<ModelCapabilities> capabilities, String provider) {
}
