package io.github.leobej.application.api.comment.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Mention someone with @[Their Name](their-user-id) - the token the editor inserts from the
// member search. Plain "@name" is just text.
public record CommentRequest(@NotBlank @Size(max = 10000) String content) {}
