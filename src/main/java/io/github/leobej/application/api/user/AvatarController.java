package io.github.leobej.application.api.user;

import io.github.leobej.domain.service.UserProfileService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Duration;
import java.util.UUID;

// What every avatarUrl points at. Public, because an <img> cannot send a bearer token: it reveals
// only the current avatar of a user whose id and avatar id the caller already has - the same
// exposure as a profile picture anywhere else.
@RestController
@RequestMapping("/api/v1/avatars")
public class AvatarController {

    // Well inside the signed link's hour, so a cached redirect never points at an expired link.
    private static final Duration REDIRECT_CACHE = Duration.ofMinutes(10);

    private final UserProfileService userProfileService;

    public AvatarController(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    @GetMapping("/{userId}/{avatarId}")
    public ResponseEntity<Void> avatar(@PathVariable UUID userId, @PathVariable UUID avatarId) {
        return userProfileService.avatarLink(userId, avatarId)
                .<ResponseEntity<Void>>map(link -> ResponseEntity.status(HttpStatus.FOUND)
                        .location(URI.create(link))
                        .cacheControl(CacheControl.maxAge(REDIRECT_CACHE).cachePrivate())
                        .build())
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
