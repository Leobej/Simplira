package io.github.leobej.application.api.attachment;

import io.github.leobej.application.api.attachment.response.AttachmentResponse;
import io.github.leobej.application.shared.response.ApiResponse;
import io.github.leobej.domain.model.user.User;
import io.github.leobej.domain.service.AttachmentService;
import io.github.leobej.infrastructure.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class AttachmentController {

    private final AttachmentService attachmentService;

    public AttachmentController(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @GetMapping("/issues/{issueId}/attachments")
    public ApiResponse<List<AttachmentResponse>> list(@PathVariable UUID issueId, @CurrentUser User user) {
        return ApiResponse.success(attachmentService.list(issueId, user.getId()).stream()
                .map(AttachmentResponse::from)
                .toList());
    }

    // multipart/form-data with the image in a part named "file". Read whole: the 10 MB cap
    // (spring.servlet.multipart) bounds it, and the type check needs the bytes anyway.
    @PostMapping(value = "/issues/{issueId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AttachmentResponse> upload(@PathVariable UUID issueId,
                                                  @RequestParam("file") MultipartFile file,
                                                  @CurrentUser User user) throws IOException {
        return ApiResponse.success(AttachmentResponse.from(
                attachmentService.upload(issueId, file.getOriginalFilename(), file.getBytes(), user.getId())));
    }

    @DeleteMapping("/attachments/{attachmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID attachmentId, @CurrentUser User user) {
        attachmentService.delete(attachmentId, user.getId());
    }
}
