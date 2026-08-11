package com.pairing.admin.global.port.out;

import org.springframework.web.multipart.MultipartFile;

/**
 * 파일 스토리지 아웃바운드 포트. 백엔드와 <b>같은 버킷</b>을 바라본다.
 *
 * <p>DB에는 object key(상대경로)만 저장하고, 응답할 때 CDN 루트를 앞에 붙여 절대 URL을 만든다.
 * 백엔드가 올린 파일도 같은 규칙이라 key 만 알면 그대로 읽고 지울 수 있다.
 */
public interface FileStoragePort {

    /** 파일을 업로드하고 저장된 object key(상대경로)를 반환한다. */
    String uploadFile(MultipartFile file, String directory);

    /** object key(상대경로)로 파일을 삭제한다. */
    void deleteFile(String key);

    /** object key 를 절대 URL로 바꾼다. */
    String toUrl(String key);
}
