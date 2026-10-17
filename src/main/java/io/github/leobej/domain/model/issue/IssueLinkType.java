package io.github.leobej.domain.model.issue;

// Read source-to-target: "A BLOCKS B". There is no BLOCKED_BY; the direction of BLOCKS covers it (§4).
public enum IssueLinkType {
    RELATED_TO,
    DUPLICATE_OF,
    BLOCKS,
    DEPENDS_ON
}
