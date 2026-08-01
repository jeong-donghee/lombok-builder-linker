package io.github.jeongdonghee.lombokbuilderlinker.model;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@code @Builder} / {@code @SuperBuilder} 가 붙은 선언 하나를 해석한 결과.
 *
 * <p>공식문서 기준 붙을 수 있는 자리는 TYPE / CONSTRUCTOR / METHOD 세 가지이고, 자리에 따라
 * "생성된 빌더가 놓이는 클래스"와 "빌더가 만들어내는 타입"이 달라진다.
 * <ul>
 *   <li><b>클래스</b>에 붙으면 — 놓이는 곳 = 그 클래스, 만드는 타입 = 그 클래스.
 *       문서 표현으로는 {@code @AllArgsConstructor(access = AccessLevel.PACKAGE)} 를 붙이고
 *       그 생성자에 {@code @Builder} 를 적용한 것처럼 동작한다.</li>
 *   <li><b>생성자</b>에 붙으면 — 놓이는 곳 = 생성자를 담은 클래스, 만드는 타입 = 그 클래스.
 *       직접 쓴 생성자가 있으면 위 방식이 안 되므로 문서가 이 형태를 권한다.</li>
 *   <li><b>메서드</b>(static·instance)에 붙으면 — 놓이는 곳 = 메서드를 담은 클래스,
 *       만드는 타입 = 메서드의 반환형.</li>
 * </ul>
 */
public final class BuilderTarget {

    private final PsiModifierListOwner declaration;
    private final PsiAnnotation annotation;
    private final PsiClass hostClass;
    private final @Nullable PsiClass builtClass;

    private BuilderTarget(@NotNull PsiModifierListOwner declaration,
                          @NotNull PsiAnnotation annotation,
                          @NotNull PsiClass hostClass,
                          @Nullable PsiClass builtClass) {
        this.declaration = declaration;
        this.annotation = annotation;
        this.hostClass = hostClass;
        this.builtClass = builtClass;
    }

    /** 클래스 · 생성자 · 메서드 중 하나에 {@code @Builder} 가 붙어 있으면 해석해 돌려준다. */
    public static @Nullable BuilderTarget of(@Nullable PsiElement declaration) {
        if (!(declaration instanceof PsiModifierListOwner owner)) {
            return null;
        }
        PsiAnnotation annotation = LombokAnnotations.findBuilder(owner);
        return annotation == null ? null : from(owner, annotation);
    }

    /** 애노테이션 쪽에서 거꾸로 — 그 애노테이션이 붙은 선언을 찾아 해석한다. */
    public static @Nullable BuilderTarget ofAnnotation(@Nullable PsiAnnotation annotation) {
        if (annotation == null || !LombokAnnotations.isBuilderAnnotation(LombokAnnotations.qualifiedName(annotation))) {
            return null;
        }
        PsiModifierListOwner owner = PsiTreeUtil.getParentOfType(annotation, PsiModifierListOwner.class, true);
        return owner == null ? null : from(owner, annotation);
    }

    private static @Nullable BuilderTarget from(@NotNull PsiModifierListOwner owner,
                                                @NotNull PsiAnnotation annotation) {
        if (owner instanceof PsiClass onClass) {
            return new BuilderTarget(owner, annotation, onClass, onClass);
        }
        if (owner instanceof PsiMethod method) {
            PsiClass host = method.getContainingClass();
            if (host == null) {
                return null;
            }
            // 생성자는 반환형이 없다 — 담은 클래스가 곧 만들어지는 타입이다.
            PsiClass built = method.isConstructor()
                ? host
                : resolveClass(method.getReturnType());
            return new BuilderTarget(owner, annotation, host, built);
        }
        return null;
    }

    private static @Nullable PsiClass resolveClass(@Nullable PsiType type) {
        return type == null ? null : PsiUtil.resolveClassInClassTypeOnly(type);
    }

    public @NotNull PsiAnnotation annotation() {
        return annotation;
    }

    /** 생성된 진입 메서드({@code builder()})와 빌더 클래스가 놓이는 클래스. */
    public @NotNull PsiClass hostClass() {
        return hostClass;
    }

    /** 클래스가 아니라 생성자·메서드에 붙은 경우 — 실측에서 연결이 끊기는 자리다. */
    public boolean isOnMember() {
        return declaration instanceof PsiMethod;
    }

    /**
     * 빌더 진입 메서드 이름. 빈 문자열이면 진입 메서드를 만들지 않는다는 뜻이므로
     * {@code null} 로 바꿔 돌려준다({@link #findBuilderMethod()} 가 "없음"으로 다룬다).
     */
    private @Nullable String builderMethodName() {
        String declared = LombokAnnotations.declaredString(annotation, LombokAnnotations.ATTR_BUILDER_METHOD_NAME);
        if (declared == null) {
            return LombokAnnotations.DEFAULT_BUILDER_METHOD_NAME;
        }
        return LombokAnnotations.SUPPRESSED.equals(declared) ? null : declared;
    }

    /** 빌더 클래스 이름. 안 적었으면 "만들어지는 타입 이름 + Builder". */
    private @Nullable String builderClassName() {
        String declared = LombokAnnotations.declaredString(annotation, LombokAnnotations.ATTR_BUILDER_CLASS_NAME);
        if (declared != null && !declared.isEmpty()) {
            return declared;
        }
        String builtName = builtClass == null ? null : builtClass.getName();
        return builtName == null ? null : builtName + "Builder";
    }

    /** Lombok 이 만들어 둔 빌더 클래스. Lombok 플러그인이 꺼져 있으면 {@code null}. */
    public @Nullable PsiClass findBuilderClass() {
        String name = builderClassName();
        return name == null ? null : hostClass.findInnerClassByName(name, false);
    }

    /** Lombok 이 만들어 둔 빌더 진입 메서드. */
    public @Nullable PsiMethod findBuilderMethod() {
        String name = builderMethodName();
        if (name == null) {
            return null;
        }
        PsiMethod[] found = hostClass.findMethodsByName(name, false);
        return found.length == 0 ? null : found[0];
    }
}
