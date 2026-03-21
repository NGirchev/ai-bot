package io.github.ngirchev.opendaimon.common.ai;

import java.util.Set;

public interface ModelDescriptionCache {
    Set<ModelCapabilities> getCapabilities(String modelId);
}
