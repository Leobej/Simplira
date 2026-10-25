package io.github.leobej.domain.model.token;

// Why a refresh token stopped working. Only ROTATED makes a later use of it suspicious: an honest
// client throws a rotated token away, but a device that was signed out from elsewhere still
// holds its old one and will try it once.
public enum RevocationReason {
    ROTATED,
    LOGGED_OUT,
    SIGNED_OUT_EVERYWHERE
}
