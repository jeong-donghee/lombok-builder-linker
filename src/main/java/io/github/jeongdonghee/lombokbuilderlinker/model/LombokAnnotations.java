package io.github.jeongdonghee.lombokbuilderlinker.model;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Lombok 애노테이션의 정규화된 이름과 속성 이름, 그리고 애노테이션에서 값을 꺼내는 도우미.
 *
 * <p>기본값은 공식 javadoc({@code lombok.Builder})에 적힌 것을 그대로 쓴다:
 * {@code builderMethodName = "builder"}, {@code buildMethodName = "build"},
 * {@code builderClassName = ""}(비어 있으면 "만들어지는 타입 이름 + Builder"),
 * {@code setterPrefix = ""}.
 */
public final class LombokAnnotations {

    public static final String BUILDER = "lombok.Builder";
    public static final String SUPER_BUILDER = "lombok.experimental.SuperBuilder";
    public static final String OBTAIN_VIA = "lombok.Builder.ObtainVia";

    /** {@code @Builder} 의 문자열 속성들. */
    public static final String ATTR_BUILDER_METHOD_NAME = "builderMethodName";
    public static final String ATTR_BUILD_METHOD_NAME = "buildMethodName";
    public static final String ATTR_BUILDER_CLASS_NAME = "builderClassName";
    public static final String ATTR_SETTER_PREFIX = "setterPrefix";

    /** {@code @Builder.ObtainVia} 의 속성들 — 여기 적힌 이름은 <b>직접 쓴</b> 멤버를 가리킨다. */
    public static final String ATTR_VIA_METHOD = "method";
    public static final String ATTR_VIA_FIELD = "field";

    public static final String DEFAULT_BUILDER_METHOD_NAME = "builder";
    public static final String DEFAULT_BUILD_METHOD_NAME = "build";
    /** 빈 문자열은 "빌더 진입 메서드를 만들지 말라"는 뜻이다(공식문서). */
    public static final String SUPPRESSED = "";

    private LombokAnnotations() {}

    /**
     * 애노테이션의 정규화된 이름. {@code @Builder.ObtainVia} 처럼 중첩 애노테이션을 짧게 쓴 경우
     * {@link PsiAnnotation#getQualifiedName()} 이 짧은 이름을 줄 수 있어, 먼저 타입을 해석해본다.
     */
    public static @Nullable String qualifiedName(@NotNull PsiAnnotation annotation) {
        PsiJavaCodeReferenceElement ref = annotation.getNameReferenceElement();
        if (ref != null && ref.resolve() instanceof PsiClass resolved) {
            String fqn = resolved.getQualifiedName();
            if (fqn != null) {
                return fqn;
            }
        }
        return annotation.getQualifiedName();
    }

    /** {@code @Builder} 또는 {@code @SuperBuilder} 중 먼저 찾은 것. */
    public static @Nullable PsiAnnotation findBuilder(@Nullable PsiModifierListOwner owner) {
        if (owner == null) {
            return null;
        }
        PsiModifierList modifiers = owner.getModifierList();
        if (modifiers == null) {
            return null;
        }
        PsiAnnotation builder = modifiers.findAnnotation(BUILDER);
        return builder != null ? builder : modifiers.findAnnotation(SUPER_BUILDER);
    }

    public static boolean isBuilderAnnotation(@Nullable String qualifiedName) {
        return BUILDER.equals(qualifiedName) || SUPER_BUILDER.equals(qualifiedName);
    }

    /**
     * <b>직접 적어둔</b> 문자열 속성 값. 기본값은 여기서 채우지 않는다 — 적었는지 여부 자체가
     * 의미를 갖는 속성({@code builderMethodName = ""})이 있어 호출부에서 구분해야 한다.
     *
     * <p>상수 참조({@code builderMethodName = SOME_CONST})처럼 리터럴이 아닌 경우는 없는 것으로 본다.
     * 그런 코드에 참조를 잘못 심는 것보다 아무것도 하지 않는 편이 안전하다.
     */
    public static @Nullable String declaredString(@Nullable PsiAnnotation annotation,
                                                  @NotNull String attribute) {
        if (annotation == null) {
            return null;
        }
        return literalString(annotation.findDeclaredAttributeValue(attribute));
    }

    /**
     * 이 요소가 Lombok 애노테이션 안(= 이름을 <b>정하는</b> 자리)에 있는가.
     *
     * <p>이 플러그인이 심은 이름 문자열 참조는 생성된 멤버를 가리키므로, 그 멤버의 참조를 검색하면
     * <b>자기 자신도 결과에 들어온다</b>. 그런데 그 문자열은 멤버를 "쓰는 자리"가 아니라 "이름을 정하는
     * 자리"다. 사용처 목록이나 이동 후보에 넣으면 자기 자신으로 돌아가는 항목이 생기므로 걸러낸다.
     */
    public static boolean isInsideLombokAnnotation(@Nullable PsiElement element) {
        PsiAnnotation annotation = PsiTreeUtil.getParentOfType(element, PsiAnnotation.class, false);
        while (annotation != null) {
            String fqn = qualifiedName(annotation);
            if (isBuilderAnnotation(fqn) || OBTAIN_VIA.equals(fqn)) {
                return true;
            }
            annotation = PsiTreeUtil.getParentOfType(annotation, PsiAnnotation.class, true);
        }
        return false;
    }

    private static @Nullable String literalString(@Nullable PsiAnnotationMemberValue value) {
        if (value instanceof PsiLiteralExpression literal && literal.getValue() instanceof String text) {
            return text;
        }
        return null;
    }
}
