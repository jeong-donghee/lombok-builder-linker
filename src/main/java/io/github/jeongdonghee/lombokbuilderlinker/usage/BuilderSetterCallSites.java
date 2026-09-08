package io.github.jeongdonghee.lombokbuilderlinker.usage;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.Processor;
import io.github.jeongdonghee.lombokbuilderlinker.model.BuilderTarget;
import io.github.jeongdonghee.lombokbuilderlinker.model.LombokAnnotations;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * {@code @Builder} 가 붙은 <b>생성자·메서드의 파라미터</b>와 그 파라미터가 만들어낸 세터의 호출부를 잇는다.
 *
 * <p><b>왜 파라미터만인가</b>(2026-09-07 실측). 세터의 출처는 두 가지다. {@code @Builder} 가
 * 클래스에 붙으면 <b>필드</b>가, 생성자·정적 메서드에 붙으면 <b>파라미터</b>가 세터가 된다. 필드
 * 쪽은 Lombok 플러그인이 이미 이어 준다 — {@code LombokFieldFindUsagesHandlerFactory} 가
 * {@code findUsagesHandlerFactory} 로 등록돼 있고, 이름 그대로 {@code PsiField} 만 다룬다.
 * 그래서 파라미터에는 그 혜택이 없다. 실측 결과가 정확히 그랬다 — 필드는 호출부가 잡히고
 * (Caller.java 1건), 생성자·정적 메서드 파라미터는 0건이었다.
 *
 * <p>반대 방향(⌘+Click)은 이미 동작한다. Lombok 이 세터 light 메서드의 navigation element 를 그
 * 필드·파라미터로 잡아두기 때문이다(실측: 세 경우 모두 {@code GTD} 판정, 착지점이 파라미터 자신).
 * 그래서 여기서는 <b>사용처와 이름 변경만</b> 이어 준다.
 *
 * <p><b>{@code @Singular} 파라미터는 이름이 같은 쪽만 잡는다.</b> {@code @Singular("item")
 * List<String> items} 는 {@code items(...)} 와 {@code item(...)} 둘을 만드는데, 파라미터 이름이
 * 정하는 것은 {@code items} 쪽뿐이다. 단수 이름은 {@code @Singular} 의 문자열이 따로 정하므로
 * 그쪽 문자열이 자기 몫으로 다룬다 — 여기서 함께 잡으면 파라미터를 rename 할 때 단수 메서드까지
 * 잘못 고치게 된다.
 */
final class BuilderSetterCallSites {

    /** 재진입 방지. 아래에서 세터의 참조를 다시 검색한다. */
    private static final ThreadLocal<Boolean> SEARCHING = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private BuilderSetterCallSites() {}

    /**
     * 이 파라미터가 만들어낸 세터. {@code @Builder} 가 생성자·메서드에 붙은 경우에만 값이 있다.
     *
     * <p>이름 규칙을 계산해 단정하지 않고 후보 중 <b>실제로 있는 것</b>을 고른다
     * ({@code setterPrefix = "with"} 면 {@code withUserName}, 없으면 {@code userName}).
     */
    static @Nullable PsiMethod setterFor(@NotNull PsiParameter parameter) {
        if (!(parameter.getDeclarationScope() instanceof PsiMethod method)) {
            return null;
        }
        BuilderTarget target = BuilderTarget.of(method);
        if (target == null || !target.isOnMember()) {
            // 클래스에 붙은 경우는 출처가 필드이고, 그쪽은 Lombok 플러그인이 이미 이어 준다.
            return null;
        }
        PsiClass builderClass = target.findBuilderClass();
        String name = parameter.getName();
        if (builderClass == null || name.isEmpty()) {
            return null;
        }
        for (String candidate : candidates(target.setterPrefix(), name)) {
            PsiMethod[] found = builderClass.findMethodsByName(candidate, false);
            if (found.length > 0) {
                return found[0];
            }
        }
        return null;
    }

    /** 세터 이름에서 파라미터 이름이 정하지 않는 앞부분({@code setterPrefix}). */
    static @NotNull String prefixOf(@NotNull PsiParameter parameter, @NotNull PsiMethod setter) {
        String name = parameter.getName();
        String setterName = setter.getName();
        return setterName.equals(name) ? "" : setterName.substring(0, setterName.length() - name.length());
    }

    private static List<String> candidates(@NotNull String prefix, @NotNull String name) {
        return prefix.isEmpty()
            ? List.of(name)
            : List.of(LombokAnnotations.accessorName(prefix, name), name);
    }

    /** 이 파라미터의 사용처로 세터 호출부를 보고한다. */
    static void report(@NotNull PsiParameter parameter,
                       @NotNull SearchScope scope,
                       @NotNull Processor<? super PsiReference> consumer) {
        if (Boolean.TRUE.equals(SEARCHING.get())) {
            return;
        }
        PsiMethod setter = setterFor(parameter);
        if (setter == null) {
            return;
        }
        String prefix = prefixOf(parameter, setter);

        SEARCHING.set(Boolean.TRUE);
        try {
            ReferencesSearch.search(setter, scope, false).forEach((PsiReference found) -> {
                // 이름을 정하는 자리(애노테이션 문자열)는 쓰는 자리가 아니다.
                if (LombokAnnotations.isInsideLombokAnnotation(found.getElement())) {
                    return true;
                }
                return consumer.process(new SetterCallReference(found, parameter, prefix));
            });
        } finally {
            SEARCHING.set(Boolean.FALSE);
        }
    }

    /**
     * 세터 호출을 "이 파라미터의 사용처"로 다시 포장한 것.
     *
     * <p>{@link BuilderCallSites} 쪽 포장과 달리 <b>이름 변경을 막지 않는다</b>. 여기서 참조가 덮고
     * 있는 텍스트는 파라미터 이름에서 나온 세터 이름이라, 파라미터를 바꾸면 함께 바뀌는 것이 맞다.
     * 접두사가 있으면 그 규칙대로 다시 조립한다({@code userName} &rarr; {@code loginName} 이면
     * {@code withUserName} &rarr; {@code withLoginName}).
     */
    private static final class SetterCallReference extends PsiReferenceBase<PsiElement> {

        private final PsiReference origin;
        private final PsiParameter parameter;
        private final String prefix;

        private SetterCallReference(@NotNull PsiReference origin,
                                    @NotNull PsiParameter parameter,
                                    @NotNull String prefix) {
            super(origin.getElement(), safeRange(origin), true);
            this.origin = origin;
            this.parameter = parameter;
            this.prefix = prefix;
        }

        private static TextRange safeRange(@NotNull PsiReference origin) {
            TextRange range = origin.getRangeInElement();
            return range == null ? TextRange.EMPTY_RANGE : range;
        }

        @Override
        public @Nullable PsiElement resolve() {
            return parameter;
        }

        @Override
        public boolean isReferenceTo(@NotNull PsiElement element) {
            return getElement().getManager().areElementsEquivalent(parameter, element);
        }

        @Override
        public PsiElement handleElementRename(@NotNull String newElementName) {
            return origin.handleElementRename(LombokAnnotations.accessorName(prefix, newElementName));
        }
    }
}
