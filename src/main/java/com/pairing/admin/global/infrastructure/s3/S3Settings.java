package com.pairing.admin.global.infrastructure.s3;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * S3 설정. 백엔드(Pairing-backend)의 S3Settings 와 같은 규칙을 그대로 따른다.
 *
 * <p><b>백엔드와 같은 값을 주입해야 한다.</b> 특히 {@code keyPrefix} 가 다르면
 * 백엔드가 올린 파일을 관리자 서버가 찾지 못한다. 같은 버킷을 보면서 서로 다른 폴더를 뒤지게 되기 때문이다.
 *
 * <p>백엔드와 달리 버킷명이 비어 있어도 기동을 막지 않는다. 관리자 서버의 주 기능은 조회·상태 변경이고,
 * 파일 업로드는 부가 기능이라 그것 때문에 관리 화면 전체가 뜨지 않는 편이 더 나쁘다.
 * 대신 업로드를 시도하는 순간 명확한 에러를 낸다.
 */
@Slf4j
@Getter
@Component
public class S3Settings {

    /** 단일 버킷명. 백엔드와 같은 값. */
    private final String bucket;

    /** AWS 리전. */
    private final String region;

    /** 응답 URL 조합용 루트. key 앞에 붙는다. (trailing slash 제거) */
    private final String cdnBase;

    /** 환경 구분용 object key prefix. 백엔드와 반드시 같은 값이어야 한다. */
    private final String keyPrefix;

    public S3Settings(
            @Value("${cloud.aws.s3.bucket:}") String bucket,
            @Value("${cloud.aws.region.static:ap-northeast-2}") String region,
            @Value("${cloud.aws.s3.cdn-url:}") String cdnOverride,
            @Value("${cloud.aws.s3.key-prefix:}") String keyPrefix
    ) {
        // 환경변수에 실수로 붙은 앞뒤 공백·개행이 섞이면 AWS가 잘못된 버킷/호스트로 인식한다.
        this.bucket = bucket == null ? "" : bucket.trim();
        this.region = region == null ? "" : region.trim();
        this.cdnBase = resolveCdnBase(cdnOverride);
        // 앞뒤 슬래시를 떼어 둔다. key 를 만들 때 하나만 붙이므로 "//" 가 생기면 S3에서 빈 폴더가 된다.
        this.keyPrefix = keyPrefix == null ? "" : keyPrefix.trim().replaceAll("^/+|/+$", "");

        if (this.bucket.isBlank()) {
            log.warn("[S3Settings] S3_BUCKET 이 비어 있습니다. 파일 업로드/삭제 기능은 동작하지 않습니다.");
        } else {
            log.info("[S3Settings] bucket={}, region={}, cdnBase={}, keyPrefix={}",
                    this.bucket, this.region, this.cdnBase, this.keyPrefix.isBlank() ? "(없음)" : this.keyPrefix);
        }
    }

    public boolean isConfigured() {
        return !bucket.isBlank();
    }

    /** prefix 를 붙인 최종 object key. prefix 가 비어 있으면 원래 key 를 그대로 돌려준다. */
    public String withPrefix(String key) {
        return keyPrefix.isBlank() ? key : keyPrefix + "/" + key;
    }

    /** object key 를 절대 URL로 바꾼다. */
    public String toUrl(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        return cdnBase + "/" + (key.startsWith("/") ? key.substring(1) : key);
    }

    private String resolveCdnBase(String cdnOverride) {
        String base = (cdnOverride != null && !cdnOverride.isBlank())
                // 직접 지정한 CDN 루트를 최우선으로 사용 (예: CloudFront 도메인)
                ? cdnOverride.trim()
                // 기본은 virtual-hosted 형식: https://{bucket}.s3.{region}.amazonaws.com
                : "https://" + bucket + ".s3." + region + ".amazonaws.com";

        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }
}
