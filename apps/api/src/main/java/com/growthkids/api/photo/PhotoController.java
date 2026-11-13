package com.growthkids.api.photo;

import com.growthkids.api.common.security.CurrentUser;
import com.growthkids.api.common.web.PageQuery;
import com.growthkids.api.common.web.PageResponse;
import com.growthkids.api.photo.dto.PhotoResponse;
import com.growthkids.api.photo.dto.PresignPhotoRequest;
import com.growthkids.api.photo.dto.PresignPhotoResponse;
import com.growthkids.api.photo.service.PhotoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 照片 API（TASK-013，规范 §47/§48）：
 * 上传流程 presign → 客户端直传签名 URL → finalize（§46）。
 */
@RestController
@Tag(name = "Photos", description = "成长照片")
public class PhotoController {

    private final PhotoService photoService;

    public PhotoController(PhotoService photoService) {
        this.photoService = photoService;
    }

    @PostMapping("/api/v1/children/{childId}/photos/presign")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "申请照片上传",
            description = "校验格式/大小后创建 pending 记录并签发短期上传 URL（§47）")
    public PresignPhotoResponse presign(
            @PathVariable long childId, @Valid @RequestBody PresignPhotoRequest request) {
        return photoService.presign(CurrentUser.id(), childId, request);
    }

    @PostMapping("/api/v1/photos/{photoId}/finalize")
    @Operation(
            summary = "完成照片上传",
            description = "校验已上传对象后进入异步处理（缩略图/EXIF），返回当前状态（§48）")
    public PhotoResponse finalizeUpload(@PathVariable long photoId) {
        return photoService.finalize(CurrentUser.id(), photoId);
    }

    @DeleteMapping("/api/v1/photos/{photoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "删除照片", description = "删除记录并同步清理存储文件（幂等）")
    public void delete(@PathVariable long photoId) {
        photoService.delete(CurrentUser.id(), photoId);
    }

    @GetMapping("/api/v1/children/{childId}/photos")
    @Operation(summary = "照片列表", description = "分页返回，附带短期签名访问 URL（§120 规则 29）")
    public PageResponse<PhotoResponse> list(
            @PathVariable long childId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        return photoService.list(CurrentUser.id(), childId, PageQuery.of(page, pageSize));
    }
}
