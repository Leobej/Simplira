package io.github.leobej.application.api.label.response;

import io.github.leobej.domain.model.label.Label;

import java.util.UUID;

public record LabelResponse(UUID id, UUID projectId, String name, String color) {
    public static LabelResponse from(Label label) {
        return new LabelResponse(label.getId(), label.getProjectId(), label.getName(), label.getColor());
    }
}
