package io.github.leobej.domain.port;

import io.github.leobej.domain.model.user.User;

// The domain asks for a token; JWT is an infrastructure detail.
public interface AccessTokenIssuer {
    String issueAccessToken(User user);
    long accessTokenExpirySeconds();
}
