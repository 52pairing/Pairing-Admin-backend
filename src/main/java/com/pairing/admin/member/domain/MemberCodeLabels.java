package com.pairing.admin.member.domain;

import java.util.Map;

/**
 * DB에 문자열로 저장된 코드값의 한글 표시명.
 *
 * <p><b>백엔드 {@code com.pairing.meta.domain.model} 패키지의 사본이다.</b>
 * 원본은 백엔드의 enum 이고 여기는 화면에 쓸 라벨만 복제한다.
 *
 * <p>enum 으로 만들지 않고 {@code Map} 으로 둔 이유가 있다. 백엔드에 값이 추가됐는데 여기에
 * 없으면 {@code valueOf} 는 {@code IllegalArgumentException} 으로 <b>회원 상세 조회 전체를
 * 터뜨린다.</b> 반면 맵은 {@link #labelOf} 가 코드 원문을 그대로 돌려주므로, 최악이라도
 * 화면에 "SPRING_BOOT" 같은 코드가 보일 뿐 조회는 계속 된다.
 * 관리 화면에서 라벨 하나 때문에 페이지가 죽는 것보다 낫다.
 *
 * <p>그래서 이 파일이 백엔드보다 뒤처져도 장애가 되지는 않는다. 다만 화면에 코드가 그대로
 * 보이기 시작하면 여기에 값을 추가하라는 신호다.
 */
public final class MemberCodeLabels {

    private MemberCodeLabels() {
    }

    /**
     * 코드를 한글 표시명으로 바꾼다.
     *
     * @return 모르는 코드면 코드 원문. null 이면 null
     */
    public static String labelOf(Map<String, String> labels, String code) {
        if (code == null) {
            return null;
        }
        return labels.getOrDefault(code, code);
    }

    /**
     * 등급. <b>역할마다 값 체계가 다르다.</b> 클라이언트는 실버/골드/다이아,
     * 프리랜서는 주니어/시니어/마스터다. 한 맵에 합치면 역할을 잘못 짚었을 때 조용히 틀린 라벨이 나온다.
     */
    public static final Map<String, String> CLIENT_GRADE = Map.of(
            "SILVER", "실버",
            "GOLD", "골드",
            "DIAMOND", "다이아");

    public static final Map<String, String> FREELANCER_GRADE = Map.of(
            "JUNIOR", "주니어",
            "SENIOR", "시니어",
            "MASTER", "마스터");

    public static final Map<String, String> JOB_CATEGORY = Map.of(
            "DEVELOPMENT", "개발",
            "DESIGN", "디자인");

    public static final Map<String, String> WORK_STYLE = Map.of(
            "REMOTE", "재택",
            "ONSITE", "상주",
            "ANY", "모두 가능");

    public static final Map<String, String> WORK_FORM = Map.of(
            "FULL_TIME", "풀타임",
            "PART_TIME", "파트타임",
            "ANY", "모두 가능");

    public static final Map<String, String> PAY_UNIT = Map.of(
            "HOURLY", "시급",
            "DAILY", "일급",
            "MONTHLY", "월급");

    public static final Map<String, String> PERIOD_UNIT = Map.of(
            "MONTH", "개월",
            "WEEK", "주");

    public static final Map<String, String> SKILL_LEVEL = Map.of(
            "BEGINNER", "초급",
            "INTERMEDIATE", "중급",
            "ADVANCED", "고급");

    public static final Map<String, String> JOB_ROLE = Map.ofEntries(
            Map.entry("FRONTEND", "프론트엔드 개발자"),
            Map.entry("BACKEND", "백엔드 개발자"),
            Map.entry("FULLSTACK", "풀스택 개발자"),
            Map.entry("WEB_PUBLISHER", "웹 퍼블리셔"),
            Map.entry("IOS", "iOS 개발자"),
            Map.entry("ANDROID", "Android 개발자"),
            Map.entry("CROSS_PLATFORM", "크로스플랫폼 개발자"),
            Map.entry("DATA_ENGINEER", "데이터 엔지니어"),
            Map.entry("DATA_ANALYST", "데이터 분석가"),
            Map.entry("AI_ML", "AI·ML 엔지니어"),
            Map.entry("DEVOPS", "DevOps 엔지니어"),
            Map.entry("CLOUD_INFRA", "클라우드·인프라 엔지니어"),
            Map.entry("DBA", "DBA"),
            Map.entry("SECURITY", "보안 엔지니어"),
            Map.entry("GAME", "게임 개발자"),
            Map.entry("BLOCKCHAIN", "블록체인 개발자"),
            Map.entry("EMBEDDED", "임베디드·하드웨어 개발자"),
            Map.entry("QA", "QA 엔지니어"),
            Map.entry("UX_UI_DESIGNER", "UX·UI 디자이너"),
            Map.entry("PRODUCT_DESIGNER", "제품 디자이너"),
            Map.entry("WEB_DESIGNER", "웹 디자이너"),
            Map.entry("GRAPHIC_DESIGNER", "그래픽 디자이너"),
            Map.entry("BX_DESIGNER", "BX·브랜드 디자이너"),
            Map.entry("ILLUSTRATOR", "일러스트레이터"),
            Map.entry("MOTION_DESIGNER", "모션·영상 디자이너"),
            Map.entry("THREE_D_DESIGNER", "3D 디자이너"));

    /**
     * 기술 스택.
     *
     * <p>대부분은 코드를 예쁘게 편 것이지만 {@code DOTNET → C#/.NET},
     * {@code SCIKIT_LEARN → scikit-learn} 처럼 규칙으로 유도할 수 없는 것이 섞여 있다.
     * 그래서 문자열 변환이 아니라 표로 둔다.
     */
    public static final Map<String, String> SKILL_CODE = Map.ofEntries(
            Map.entry("REACT", "React"), Map.entry("NEXT_JS", "Next.js"),
            Map.entry("VUE", "Vue"), Map.entry("TYPESCRIPT", "TypeScript"),
            Map.entry("JAVASCRIPT", "JavaScript"), Map.entry("ANGULAR", "Angular"),
            Map.entry("SVELTE", "Svelte"), Map.entry("REDUX", "Redux"),
            Map.entry("TAILWIND", "Tailwind"), Map.entry("JAVA", "Java"),
            Map.entry("SPRING_BOOT", "Spring Boot"), Map.entry("NODE_JS", "Node.js"),
            Map.entry("NEST_JS", "NestJS"), Map.entry("PYTHON", "Python"),
            Map.entry("DJANGO", "Django"), Map.entry("FASTAPI", "FastAPI"),
            Map.entry("GO", "Go"), Map.entry("PHP", "PHP"),
            Map.entry("LARAVEL", "Laravel"), Map.entry("DOTNET", "C#/.NET"),
            Map.entry("KOTLIN", "Kotlin"), Map.entry("SWIFT", "Swift"),
            Map.entry("FLUTTER", "Flutter"), Map.entry("REACT_NATIVE", "React Native"),
            Map.entry("SQL", "SQL"), Map.entry("PANDAS", "Pandas"),
            Map.entry("TENSORFLOW", "TensorFlow"), Map.entry("PYTORCH", "PyTorch"),
            Map.entry("SCIKIT_LEARN", "scikit-learn"), Map.entry("SPARK", "Spark"),
            Map.entry("LANGCHAIN", "LangChain"), Map.entry("MYSQL", "MySQL"),
            Map.entry("POSTGRESQL", "PostgreSQL"), Map.entry("MONGODB", "MongoDB"),
            Map.entry("REDIS", "Redis"), Map.entry("ORACLE", "Oracle"),
            Map.entry("ELASTICSEARCH", "Elasticsearch"), Map.entry("AWS", "AWS"),
            Map.entry("GCP", "GCP"), Map.entry("DOCKER", "Docker"),
            Map.entry("KUBERNETES", "Kubernetes"), Map.entry("JENKINS", "Jenkins"),
            Map.entry("GITHUB_ACTIONS", "GitHub Actions"), Map.entry("TERRAFORM", "Terraform"),
            Map.entry("NGINX", "Nginx"), Map.entry("LINUX", "Linux"),
            Map.entry("UNITY", "Unity"), Map.entry("UNREAL", "Unreal"),
            Map.entry("CPP", "C++"), Map.entry("SOLIDITY", "Solidity"),
            Map.entry("FIGMA", "Figma"), Map.entry("SKETCH", "Sketch"),
            Map.entry("ADOBE_XD", "Adobe XD"), Map.entry("PHOTOSHOP", "Photoshop"),
            Map.entry("ILLUSTRATOR", "Illustrator"), Map.entry("AFTER_EFFECTS", "After Effects"),
            Map.entry("ZEPLIN", "Zeplin"), Map.entry("GIT", "Git"),
            Map.entry("JIRA", "Jira"), Map.entry("NOTION", "Notion"),
            Map.entry("REST_API", "REST API"), Map.entry("GRAPHQL", "GraphQL"),
            Map.entry("SWAGGER", "Swagger"));
}
