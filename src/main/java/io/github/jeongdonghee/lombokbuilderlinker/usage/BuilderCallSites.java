package io.github.jeongdonghee.lombokbuilderlinker.usage;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.Processor;
import io.github.jeongdonghee.lombokbuilderlinker.model.BuilderTarget;
import io.github.jeongdonghee.lombokbuilderlinker.model.LombokAnnotations;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@code @Builder} 가 붙은 생성자·메서드의 "사용처"를 빌더 진입점의 호출부로 환산하는 공용 로직.
 *
 * <p>왜 필요한가: {@code X.builder().name("a").build()} 에서 소스에 보이는 호출은 {@code builder()}
 * 뿐이고, 생성자를 실제로 부르는 코드({@code build()} 내부)는 Lombok 이 만들어 소스에 없다. 그래서
 * 생성자에서 Find Usages 를 하면 빈 결과가 나온다 — 실측에서 ★·P02b·P04·P05·P06 5/5 전부 그랬다.
 *
 * <p>관련 티켓: IDEA-293203 (Open). {@code @Builder} 가 <b>instance 메서드</b>에 붙는 경우(P06)는
 * 어느 티켓에도 없는 미보고 케이스다.
 */
final class BuilderCallSites {

    /**
     * 재진입 방지. 아래에서 진입 메서드의 참조를 다시 검색하는데, 그 검색이 이 로직을 또 부르면
     * 무한 재귀가 된다.
     */
    private static final ThreadLocal<Boolean> SEARCHING = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private BuilderCallSites() {}

    /**
     * {@code annotated} 가 {@code @Builder} 를 단 생성자·메서드라면, 그 빌더 진입점의 호출부를
     * {@code annotated} 의 사용처로 보고한다.
     */
    static void report(@NotNull PsiMethod annotated,
                       @NotNull SearchScope scope,
                       @NotNull Processor<? super PsiReference> consumer) {
        if (Boolean.TRUE.equals(SEARCHING.get())) {
            return;
        }
        BuilderTarget target = BuilderTarget.of(annotated);
        if (target == null || !target.isOnMember()) {
            return;
        }
        PsiMethod entry = target.findBuilderMethod();
        if (entry == null || entry.isEquivalentTo(annotated)) {
            // builderMethodName = "" 로 진입점을 막았거나, @Builder 가 붙은 메서드 자신이 진입점인 경우.
            return;
        }

        SEARCHING.set(Boolean.TRUE);
        try {
            ReferencesSearch.search(entry, scope, false).forEach((PsiReference found) -> {
                // 이 플러그인이 애노테이션 이름 문자열에 심어둔 참조도 결과에 섞여 들어온다.
                // 그 문자열은 빌더를 "쓰는 자리"가 아니라 "이름을 정하는 자리"이므로 사용처가 아니다.
                if (LombokAnnotations.isInsideLombokAnnotation(found.getElement())) {
                    return true;
                }
                return consumer.process(new DisplayOnlyReference(found, annotated));
            });
        } finally {
            SEARCHING.set(Boolean.FALSE);
        }
    }

    /**
     * 다른 요소를 가리키던 참조를 "이 선언의 사용처"로 다시 포장한 것.
     *
     * <p>이름 변경은 일부러 막아둔다. 이 참조가 실제로 덮고 있는 텍스트는 {@code builder()} 라는
     * 생성된 메서드 이름이고, 생성자 이름과는 무관하기 때문이다. 막지 않으면 생성자를 rename 할 때
     * 빌더 호출부를 함께 고쳐버리는 사고가 난다.
     */
    private static final class DisplayOnlyReference extends PsiReferenceBase<PsiElement> {

        private final PsiElement target;

        private DisplayOnlyReference(@NotNull PsiReference origin, @NotNull PsiElement target) {
            super(origin.getElement(), safeRange(origin), true);
            this.target = target;
        }

        private static TextRange safeRange(@NotNull PsiReference origin) {
            TextRange range = origin.getRangeInElement();
            return range == null ? TextRange.EMPTY_RANGE : range;
        }

        @Override
        public @Nullable PsiElement resolve() {
            return target;
        }

        @Override
        public boolean isReferenceTo(@NotNull PsiElement element) {
            return getElement().getManager().areElementsEquivalent(target, element);
        }

        @Override
        public PsiElement handleElementRename(@NotNull String newElementName) {
            return getElement();
        }
    }
}
