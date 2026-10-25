package io.github.leobej.domain.service;

import io.github.leobej.domain.model.user.User;
import io.github.leobej.domain.port.ContentTypeDetector;
import io.github.leobej.domain.port.FileStorage;
import io.github.leobej.domain.repository.UserRepository;
import io.github.leobej.shared.exception.InvalidCredentialsException;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

// The signed-in user's own profile. Not in the audit log: that is workspace history, and a
// profile belongs to no workspace.
//
// Pure domain: no Spring annotations, no infrastructure imports.
// Wired as a bean by infrastructure.config.DomainServiceConfig.
public class UserProfileService {

    public static final long AVATAR_MAX_SIZE = 2L * 1024 * 1024;
    public static final Duration AVATAR_LINK_LIFETIME = Duration.ofHours(1);

    // An avatar's URL is a path on this API, not a storage link: an <img> cannot send a bearer
    // token and the bucket is private, so the path redirects to a freshly signed link (§27). Every
    // response that already carries avatarUrl works unchanged.
    private static final String URL_PREFIX = "/api/v1/avatars/";
    private static final String KEY_PREFIX = "avatars/";

    private final UserRepository userRepository;
    private final FileStorage fileStorage;
    private final ContentTypeDetector contentTypeDetector;

    public UserProfileService(UserRepository userRepository,
                              FileStorage fileStorage,
                              ContentTypeDetector contentTypeDetector) {
        this.userRepository = userRepository;
        this.fileStorage = fileStorage;
        this.contentTypeDetector = contentTypeDetector;
    }

    // Mentions keep working after a rename: the token carries the id, and the name is display only.
    public User rename(UUID userId, String fullName) {
        User user = load(userId);
        user.changeName(fullName.strip());
        return userRepository.save(user);
    }

    // A new id per upload, so a browser holding the old image never shows it in place of the new
    // one; the old object is deleted once the new one is in place.
    public User changeAvatar(UUID userId, byte[] content) {
        User user = load(userId);
        String mimeType = ImageUploadPolicy.requireImage(content, AVATAR_MAX_SIZE, contentTypeDetector);

        UUID avatarId = UUID.randomUUID();
        fileStorage.store(KEY_PREFIX + userId + "/" + avatarId, content, mimeType);
        Optional<String> previousKey = keyOf(user.getAvatarUrl());
        user.changeAvatar(URL_PREFIX + userId + "/" + avatarId);
        User saved = userRepository.save(user);
        previousKey.ifPresent(fileStorage::delete);
        return saved;
    }

    public User removeAvatar(UUID userId) {
        User user = load(userId);
        Optional<String> key = keyOf(user.getAvatarUrl());
        user.changeAvatar(null);
        User saved = userRepository.save(user);
        key.ifPresent(fileStorage::delete);
        return saved;
    }

    // Only the user's current avatar is ever signed: an old or made-up id is simply not found.
    public Optional<String> avatarLink(UUID userId, UUID avatarId) {
        String path = URL_PREFIX + userId + "/" + avatarId;
        return userRepository.findById(userId)
                .filter(user -> path.equals(user.getAvatarUrl()))
                .map(user -> fileStorage.temporaryUrl(KEY_PREFIX + userId + "/" + avatarId, AVATAR_LINK_LIFETIME,
                        "avatar"));
    }

    private static Optional<String> keyOf(String avatarUrl) {
        if (avatarUrl == null || !avatarUrl.startsWith(URL_PREFIX)) {
            return Optional.empty();
        }
        return Optional.of(KEY_PREFIX + avatarUrl.substring(URL_PREFIX.length()));
    }

    private User load(UUID userId) {
        return userRepository.findById(userId).orElseThrow(InvalidCredentialsException::new);
    }
}
