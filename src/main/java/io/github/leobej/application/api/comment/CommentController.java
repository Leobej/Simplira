package io.github.leobej.application.api.comment;

import io.github.leobej.application.api.comment.request.CommentRequest;
import io.github.leobej.application.api.comment.response.CommentResponse;
import io.github.leobej.application.shared.response.ApiResponse;
import io.github.leobej.domain.model.user.User;
import io.github.leobej.domain.service.CommentService;
import io.github.leobej.infrastructure.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @GetMapping("/issues/{issueId}/comments")
    public ApiResponse<List<CommentResponse>> list(@PathVariable UUID issueId, @CurrentUser User user) {
        return ApiResponse.success(commentService.list(issueId, user.getId()).stream()
                .map(CommentResponse::from)
                .toList());
    }

    @PostMapping("/issues/{issueId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CommentResponse> create(@PathVariable UUID issueId,
                                               @Valid @RequestBody CommentRequest request,
                                               @CurrentUser User user) {
        return ApiResponse.success(CommentResponse.from(
                commentService.create(issueId, request.content(), user.getId())));
    }

    @PatchMapping("/comments/{commentId}")
    public ApiResponse<CommentResponse> edit(@PathVariable UUID commentId,
                                             @Valid @RequestBody CommentRequest request,
                                             @CurrentUser User user) {
        return ApiResponse.success(CommentResponse.from(
                commentService.edit(commentId, request.content(), user.getId())));
    }

    @DeleteMapping("/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID commentId, @CurrentUser User user) {
        commentService.delete(commentId, user.getId());
    }
}
