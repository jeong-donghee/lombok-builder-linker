package io.github.jeongdonghee.lombokbuilderlinker.reference;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import io.github.jeongdonghee.lombokbuilderlinker.model.BuilderTarget;
import io.github.jeongdonghee.lombokbuilderlinker.model.LombokAnnotations;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * "이 이름 속성이 정하는 멤버는 무엇인가"를 한 곳에서 답한다.
 *
 * <p>같은 질문을 두 곳에서 한다 — 애노테이션 문자열의 참조({@link LombokMemberReference})와
 * 이름 변경 시 호출부 수집({@link BuilderNameRename}). 판정 규칙이 갈라지면 사용처는 찾는데
 * 이름 변경은 놓치는 식으로 어긋나므로 여기로 모았다.
 *
 * <p>Lombok 플러그인에 컴파일 의존을 걸지 않는다 — Lombok 이 {@code PsiAugmentProvider} 로 만들어 둔
 * 멤버를 표준 PSI 조회({@code findMethodsByName} · {@code findInnerClassByName})로 찾을 뿐이다.
 * 그래서 그 멤버가 증강으로 생겼든 손으로 쓰였든 같은 경로를 탄다.
 */
final class LombokGeneratedMembers {

    private LombokGeneratedMembers() {}

    /**
     * {@code annotation} 의 {@code attributeName} 속성이 {@code name} 이라는 이름으로 정하는 멤버들.
     *
     * <p>{@code setterPrefix} 만 1:N 이다(접두사 하나가 세터 여러 개에 대응). 나머지는 0개 또는 1개다.
     */
    static @NotNull List<PsiElement> of(@Nullable PsiAnnotation annotation,
                                        @NotNull String attributeName,
                                        @NotNull String name) {
        BuilderTarget target = BuilderTarget.ofAnnotation(annotation);
        if (target == null || name.isEmpty()) {
            return List.of();
        }
        return switch (attributeName) {
            case LombokAnnotations.ATTR_BUILDER_METHOD_NAME -> methods(target.hostClass(), name);
            case LombokAnnotations.ATTR_BUILD_METHOD_NAME -> methods(target.findBuilderClass(), name);
            case LombokAnnotations.ATTR_BUILDER_CLASS_NAME -> innerClass(target.hostClass(), name);
            case LombokAnnotations.ATTR_SETTER_PREFIX -> settersWithPrefix(target.findBuilderClass(), name);
            default -> List.of();
        };
    }

    private static List<PsiElement> methods(@Nullable PsiClass owner, @NotNull String name) {
        if (owner == null) {
            return List.of();
        }
        PsiMethod[] found = owner.findMethodsByName(name, false);
        return found.length == 0 ? List.of() : List.of((PsiElement[]) found);
    }

    private static List<PsiElement> innerClass(@NotNull PsiClass owner, @NotNull String name) {
        PsiClass found = owner.findInnerClassByName(name, false);
        return found == null ? List.of() : List.of(found);
    }

    private static List<PsiElement> settersWithPrefix(@Nullable PsiClass builderClass, @NotNull String prefix) {
        if (builderClass == null) {
            return List.of();
        }
        List<PsiElement> matched = new ArrayList<>();
        for (PsiMethod method : builderClass.getMethods()) {
            String name = method.getName();
            if (name.length() > prefix.length() && name.startsWith(prefix)) {
                matched.add(method);
            }
        }
        return matched;
    }
}
