package io.github.jeongdonghee.lombokbuilderlinker.usage;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.UseScopeEnlarger;
import io.github.jeongdonghee.lombokbuilderlinker.model.BuilderTarget;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@code @Builder} 가 붙은 <b>private</b> 생성자·메서드의 검색 범위를 빌더 진입점만큼 넓힌다.
 *
 * <p>왜 필요한가: 자바는 private 멤버의 use scope 를 <b>그 클래스 본문</b>으로 좁힌다
 * (실측: {@code LocalSearchScope: [PsiClass:PrivateCase]}). 클래스 밖에서는 부를 수 없으니
 * 당연한 최적화다. 그런데 {@code @Builder} 가 붙으면 이야기가 달라진다 — Lombok 이 만든
 * {@code public static} 진입 메서드가 그 생성자를 대신 불러 주므로, 실제 호출부는 <b>다른 파일</b>에
 * 있다(실측: 진입 메서드의 use scope 는 프로젝트 전역).
 *
 * <p>그 좁은 범위가 {@code getEffectiveSearchScope()} 로 {@link BuilderCallSites} 까지 그대로
 * 흘러들어와, 진입 메서드의 호출부를 <b>클래스 본문 안에서만</b> 찾다가 0건으로 끝났다. public
 * 생성자에서는 use scope 가 전역이라 같은 코드가 멀쩡히 동작했고, 그래서 이 구멍이 늦게 드러났다
 * (2026-08-04, `@Builder(builderMethodName = "alarmHistoryBuilder")` 가 붙은 private 생성자에서 보고).
 *
 * <p>범위를 여기서 넓히면 검색기뿐 아니라 unused 인스펙션·Safe Delete·Highlight Usages 까지 같은
 * 범위를 쓰게 된다. 사용자가 직접 고른 범위는 그대로 존중된다 — 플랫폼이
 * {@code 사용자 지정 범위 ∩ (use scope ∪ 여기서 더한 범위)} 로 교집합을 잡기 때문이다.
 *
 * <p>{@code entry.getUseScope()} 를 부르는 것은 의도적이다. {@code PsiSearchHelper.getUseScope} 를
 * 쓰면 진입 메서드에 대해 확장기들이 다시 도는데, 그 안에 이 클래스도 있어 재진입이 생긴다.
 */
public final class BuilderMemberUseScopeEnlarger extends UseScopeEnlarger {

    @Override
    public @Nullable SearchScope getAdditionalUseScope(@NotNull PsiElement element) {
        if (!(element instanceof PsiMethod method)) {
            return null;
        }
        BuilderTarget target = BuilderTarget.of(method);
        if (target == null || !target.isOnMember()) {
            return null;
        }
        PsiMethod entry = target.findBuilderMethod();
        if (entry == null || entry.isEquivalentTo(method)) {
            // builderMethodName = "" 로 진입점을 막았거나, @Builder 가 붙은 메서드 자신이 진입점인 경우.
            return null;
        }
        return entry.getUseScope();
    }
}
