# Java 17 실행 환경을 가져옵니다.
FROM eclipse-temurin:17-jre-alpine

# 컨테이너 내 작업 디렉토리 설정
WORKDIR /app

# CI에서 빌드해둔 app.jar 파일을 컨테이너 안으로 가져옵니다.
# (build/libs/pairing-admin.jar 를 app.jar 로 복사해서 사용)
COPY app.jar app.jar

# 관리자 서버 포트. 백엔드(8080)와 다른 포트를 쓴다.
EXPOSE 8081

# 세션 만료 시각 계산이 UTC로 어긋나지 않도록 컨테이너 타임존을 KST로 고정한다.
ENTRYPOINT ["java", "-Duser.timezone=Asia/Seoul", "-jar", "app.jar"]
