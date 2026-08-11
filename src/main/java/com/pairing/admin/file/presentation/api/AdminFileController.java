package com.pairing.admin.file.presentation.api;

import com.pairing.admin.file.presentation.api.response.UploadedFileResponse;
import com.pairing.admin.global.common.api.response.ApiResponse;
import com.pairing.admin.global.port.out.FileStoragePort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 파일 업로드. (공지 이미지, 첨부 등)
 *
 * <p>백엔드와 <b>같은 S3 버킷</b>을 쓴다는 것을 확인하는 용도로도 쓸 수 있다.
 * 업로드한 뒤 돌아온 key 를 백엔드 쪽에서 조회하면 같은 파일이 보여야 한다.
 * 보이지 않는다면 S3_BUCKET 이나 S3_KEY_PREFIX 가 두 서버에서 다른 것이다.
 */
@RestController
@RequestMapping("/api/v1/admin/files")
@RequiredArgsConstructor
@Tag(name = "90. File", description = "파일 업로드 API")
public class AdminFileController {

    private final FileStoragePort fileStoragePort;

    /** 관리자가 올린 파일이 모이는 폴더. 백엔드가 쓰는 폴더와 겹치지 않게 이름을 나눠 둔다. */
    private static final String ADMIN_DIRECTORY = "admin";

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "[관리자] 파일 업로드",
            description = "S3에 업로드하고 object key 와 조회용 URL을 반환합니다. DB에는 key를 저장하세요.")
    public ResponseEntity<ApiResponse<UploadedFileResponse>> upload(@RequestPart("file") MultipartFile file) {

        String key = fileStoragePort.uploadFile(file, ADMIN_DIRECTORY);
        UploadedFileResponse body = new UploadedFileResponse(key, fileStoragePort.toUrl(key));

        return ResponseEntity.ok(ApiResponse.created("FILE_UPLOADED", "업로드에 성공했습니다.", body));
    }

    @DeleteMapping
    @Operation(summary = "[관리자] 파일 삭제",
            description = """
                    object key 로 삭제합니다. 삭제 실패는 예외를 던지지 않고 로그만 남깁니다.
                    (스토리지 실패로 관리 작업 자체가 막히면 안 되기 때문)
                    """)
    public ResponseEntity<ApiResponse<Void>> delete(@RequestParam String key) {
        fileStoragePort.deleteFile(key);
        return ResponseEntity.ok(ApiResponse.success("FILE_DELETED", "삭제 요청을 처리했습니다."));
    }
}
