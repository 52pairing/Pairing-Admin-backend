package com.pairing.admin.global.infrastructure.s3;

import com.pairing.admin.global.exception.BusinessException;
import com.pairing.admin.global.exception.GlobalErrorCode;
import com.pairing.admin.global.port.out.FileStoragePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class S3StorageAdapter implements FileStoragePort {

    private final S3Client s3Client;
    private final S3Settings s3Settings;

    @Override
    public String uploadFile(MultipartFile file, String directory) {
        if (file == null || file.isEmpty()) {
            return null;
        }

        if (!s3Settings.isConfigured()) {
            throw new BusinessException(GlobalErrorCode.FILE_UPLOAD_FAILED,
                    "S3 버킷이 설정되지 않았습니다. S3_BUCKET 환경변수를 확인해 주세요.");
        }

        String originalFilename = file.getOriginalFilename();
        String extension = originalFilename != null && originalFilename.contains(".")
                ? originalFilename.substring(originalFilename.lastIndexOf("."))
                : "";

        // 원본 파일명은 경로 조작(../) 위험이 있어 저장 경로에 쓰지 않는다.
        // UUID로 새 이름을 만들고, 이 key가 그대로 DB에 저장된다.
        String key = s3Settings.withPrefix(directory + "/" + UUID.randomUUID() + extension);

        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(s3Settings.getBucket())
                    .key(key)
                    .contentType(file.getContentType())
                    .build();

            s3Client.putObject(request, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

            log.info("[S3 Upload] 업로드 완료: key={}", key);
            return key;

        } catch (Exception e) {
            // putObject 는 S3Exception(RuntimeException)을 던진다. IOException 만 잡으면
            // AccessDenied/NoSuchBucket 같은 진짜 원인이 로그에 남지 않는다.
            log.error("[S3 Upload Error] 업로드 실패: bucket={}, key={}, cause={}",
                    s3Settings.getBucket(), key, e.toString(), e);
            throw new BusinessException(GlobalErrorCode.FILE_UPLOAD_FAILED);
        }
    }

    @Override
    public void deleteFile(String key) {
        if (key == null || key.isBlank() || !s3Settings.isConfigured()) {
            return;
        }

        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(s3Settings.getBucket())
                    .key(key)
                    .build());
            log.info("[S3 Delete] 삭제 완료: key={}", key);
        } catch (Exception e) {
            // 삭제 실패는 본 작업(예: 엔티티 삭제)을 되돌릴 이유가 되지 않으므로 로깅만 한다.
            log.error("[S3 Delete Error] 삭제 실패: key={}, cause={}", key, e.toString());
        }
    }

    @Override
    public String toUrl(String key) {
        return s3Settings.toUrl(key);
    }
}
