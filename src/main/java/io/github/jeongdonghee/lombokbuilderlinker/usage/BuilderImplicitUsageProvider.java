package io.github.jeongdonghee.lombokbuilderlinker.usage;

import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiParameter;
import io.github.jeongdonghee.lombokbuilderlinker.model.BuilderTarget;
import io.github.jeongdonghee.lombokbuilderlinker.model.LombokAnnotations;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Lombok 이 실제로는 호출하는데 소스에 호출 코드가 없어 "사용되지 않음"으로 회색 처리되는 선언들을 막는다.
 *
 * <p>두 가지를 다룬다.
 *
 * <p><b>① {@code @Builder} 가 붙은 생성자·메서드.</b> 실제 호출자는 Lombok 이 만들어내는
 * {@code build()} 인데 그 코드는 소스에 없다. 실측에서 P02b(생성자 + {@code @NoArgsConstructor}) ·
 * P05(static 메서드) · P06(instance 메서드) 가 회색으로 죽었다. 클래스에 붙은 경우는 4/4 정상이었으므로
 * 건드리지 않는다 — 필요 없는 곳까지 "쓰이는 중"이라고 우기면 진짜 죽은 코드를 못 잡는다.
 *
 * <p><b>② {@code @Builder.ObtainVia} 가 이름으로 가리키는 멤버.</b> 이건 원래 범위에 없었다.
 * 애노테이션 문자열에 참조를 심었으니 회색도 자동으로 풀릴 것이라고 봤는데, 실제 IDE 에서
 * {@code computeLength()} 가 여전히 회색이었다. 참조를 soft 로 두었고 unused 인스펙션은 그런 참조를
 * 사용처로 세지 않기 때문이다. 참조를 hard 로 바꾸면 해석 실패 시 빨간 오류가 나므로, 참조는 soft 로
 * 두고 여기서 따로 막는다.
 *
 * <p>관련 티켓(모두 Open): IDEA-314445 · IDEA-343275 · IDEA-345743.
 */
public final class BuilderImplicitUsageProvider implements ImplicitUsageProvider {

    @Override
    public boolean isImplicitUsage(@NotNull PsiElement element) {
        return isBuilderEntryPoint(element) || isNamedByObtainVia(element);
    }

    @Override
    public boolean isImplicitRead(@NotNull PsiElement element) {
        // ObtainVia 가 가리키는 필드는 Lombok 이 값을 읽어간다.
        return isNamedByObtainVia(element);
    }

    @Override
    public boolean isImplicitWrite(@NotNull PsiElement element) {
        return false;
    }

    /** ① {@code @Builder} 가 붙은 생성자·메서드인가. */
    private static boolean isBuilderEntryPoint(@NotNull PsiElement element) {
        BuilderTarget target = BuilderTarget.of(element);
        return target != null && target.isOnMember();
    }

    /**
     * ② 같은 클래스의 어떤 {@code @Builder.ObtainVia} 가 이 멤버를 이름으로 가리키는가.
     *
     * <p>탐색 범위를 담은 클래스 안으로 한정한다 — Lombok 이 그 이름을 찾는 범위와 같고,
     * 강조 표시 중 매 선언마다 불리는 자리이므로 넓게 훑으면 편집기가 느려진다.
     */
    private static boolean isNamedByObtainVia(@NotNull PsiElement element) {
        if (!(element instanceof PsiMember member)) {
            return false;
        }
        String attribute = attributeFor(element);
        if (attribute == null) {
            return false;
        }
        String name = member.getName();
        PsiClass owner = member.getContainingClass();
        if (name == null || owner == null) {
            return false;
        }
        for (PsiField field : owner.getFields()) {
            if (namesMember(field, attribute, name)) {
                return true;
            }
        }
        // @Builder 가 생성자·메서드에 붙으면 ObtainVia 는 그 파라미터에 붙을 수 있다.
        for (PsiMethod method : owner.getMethods()) {
            for (PsiParameter parameter : method.getParameterList().getParameters()) {
                if (namesMember(parameter, attribute, name)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 메서드는 {@code method = "..."}, 필드는 {@code field = "..."} 로 가리켜진다. */
    private static @Nullable String attributeFor(@NotNull PsiElement element) {
        if (element instanceof PsiMethod) {
            return LombokAnnotations.ATTR_VIA_METHOD;
        }
        return element instanceof PsiField ? LombokAnnotations.ATTR_VIA_FIELD : null;
    }

    private static boolean namesMember(@NotNull PsiModifierListOwner holder,
                                       @NotNull String attribute,
                                       @NotNull String name) {
        PsiModifierList modifiers = holder.getModifierList();
        if (modifiers == null) {
            return false;
        }
        for (PsiAnnotation annotation : modifiers.getAnnotations()) {
            if (!LombokAnnotations.OBTAIN_VIA.equals(LombokAnnotations.qualifiedName(annotation))) {
                continue;
            }
            if (name.equals(LombokAnnotations.declaredString(annotation, attribute))) {
                return true;
            }
        }
        return false;
    }
}
