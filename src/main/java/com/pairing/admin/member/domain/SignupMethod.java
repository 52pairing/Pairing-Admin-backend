package com.pairing.admin.member.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 회원 목록 화면의 "가입방식" 필터·표시값.
 *
 * <p>{@link SignupType} 은 EMAIL / SOCIAL 두 가지뿐이라 화면이 요구하는 카카오·구글 구분이
 * 나오지 않는다. 공급자는 {@code social_account.provider} 에 따로 있어서, 두 값을 합쳐야
 * 화면에 쓸 수 있는 하나가 된다.
 *
 * <p>KAKAO / GOOGLE 은 백엔드 {@code com.pairing.account.domain.model.SocialProvider} 의
 * 이름과 같아야 한다. DB 에 그 문자열이 그대로 들어가고, 필터가 그 값으로 비교하기 때문이다.
 */
@Getter
@RequiredArgsConstructor
public enum SignupMethod {

    EMAIL("이메일"),
    KAKAO("카카오"),
    GOOGLE("구글"),

    /**
     * signup_type 은 SOCIAL 인데 social_account 행을 못 찾은 경우.
     *
     * <p>연동이 끊겼거나 데이터가 어긋난 상태다. 임의로 카카오나 구글로 뭉뚱그리면 화면이
     * 사실과 다른 값을 보여 주므로, 모른다는 것을 그대로 드러낸다.
     */
    SOCIAL("소셜");

    private final String label;

    /**
     * @param signupType account.signup_type
     * @param provider   social_account.provider. 연동이 없으면 null
     */
    public static SignupMethod of(SignupType signupType, String provider) {
        if (signupType == SignupType.EMAIL) {
            return EMAIL;
        }
        if (provider == null) {
            return SOCIAL;
        }
        return switch (provider) {
            case "KAKAO" -> KAKAO;
            case "GOOGLE" -> GOOGLE;
            default -> SOCIAL;
        };
    }
}
