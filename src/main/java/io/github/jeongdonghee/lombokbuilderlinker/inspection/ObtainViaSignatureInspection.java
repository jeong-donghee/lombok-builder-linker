package io.github.jeongdonghee.lombokbuilderlinker.inspection;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.util.PsiTreeUtil;
import io.github.jeongdonghee.lombokbuilderlinker.model.LombokAnnotations;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@code @Builder.ObtainVia} 가 가리키는 멤버가 Lombok 이 실제로 부를 수 있는 모양인지 검사한다.
 *
 * <p><b>왜 필요한가.</b> Lombok 은 {@code toBuilder()} 본문에 그 호출을 <b>그대로 심는다</b>
 * ({@code HandleBuilder}, 생성 코드 확인):
 * <ul>
 *   <li>{@code isStatic = false}(기본) &rarr; {@code this.method()} — 인자 없이 부른다.</li>
 *   <li>{@code isStatic = true} &rarr; {@code Type.method(this)} — 인스턴스 하나를 넘긴다.</li>
 * </ul>
 * 이 모양이 안 맞으면 <b>생성된 코드가 컴파일되지 않는다</b>. 그런데 그 코드는 소스에 없으므로
 * 편집기는 끝까지 조용하고, 빌드를 돌려야 비로소 빨개진다. 이 인스펙션이 그 시차를 없앤다.
 *
 * <p>Lombok 자신이 검사하는 것은 문법뿐이다 — {@code field} 와 {@code method} 를 함께 적었거나
 * {@code method} 없이 {@code isStatic = true} 를 적은 경우({@code HandleBuilder:467-472}).
 * <b>시그니처는 검사하지 않는다.</b>
 */
public final class ObtainViaSignatureInspection extends AbstractBaseJavaLocalInspectionTool {

    private static final String ATTR_IS_STATIC = "isStatic";

    @Override
    public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new JavaElementVisitor() {
            @Override
            public void visitAnnotation(@NotNull PsiAnnotation annotation) {
                if (!LombokAnnotations.OBTAIN_VIA.equals(LombokAnnotations.qualifiedName(annotation))) {
                    return;
                }
                checkMethod(annotation, holder);
                checkField(annotation, holder);
            }
        };
    }

    /** {@code method = "..."} — 이름이 있는가, 그리고 Lombok 이 부르는 모양과 맞는가. */
    private static void checkMethod(@NotNull PsiAnnotation annotation, @NotNull ProblemsHolder holder) {
        PsiAnnotationMemberValue value = annotation.findDeclaredAttributeValue(LombokAnnotations.ATTR_VIA_METHOD);
        String name = literalString(value);
        if (name == null || name.isEmpty()) {
            return;
        }
        PsiClass owner = PsiTreeUtil.getParentOfType(annotation, PsiClass.class, true);
        if (owner == null) {
            return;
        }
        PsiMethod[] candidates = owner.findMethodsByName(name, true);
        if (candidates.length == 0) {
            report(holder, value, "Lombok cannot find a method named '" + name + "' here; toBuilder() will not compile");
            return;
        }
        boolean isStatic = isStaticRequested(annotation);
        if (isStatic) {
            // Type.method(this) — static 이어야 하고 인스턴스 하나를 받아야 한다.
            if (matches(candidates, method -> method.hasModifierProperty(PsiModifier.STATIC)
                && method.getParameterList().getParametersCount() == 1)) {
                return;
            }
            report(holder, value, "With isStatic = true, Lombok calls '" + name
                + "' as a static method taking the instance: it must be static and take exactly one argument");
            return;
        }
        // this.method() — 인자 없이 부른다.
        if (matches(candidates, method -> method.getParameterList().isEmpty())) {
            return;
        }
        report(holder, value, "Lombok calls '" + name
            + "()' with no arguments, so it must take none (or add isStatic = true and take the instance)");
    }

    /** {@code field = "..."} — 그 이름의 필드가 실제로 있는가. */
    private static void checkField(@NotNull PsiAnnotation annotation, @NotNull ProblemsHolder holder) {
        PsiAnnotationMemberValue value = annotation.findDeclaredAttributeValue(LombokAnnotations.ATTR_VIA_FIELD);
        String name = literalString(value);
        if (name == null || name.isEmpty()) {
            return;
        }
        PsiClass owner = PsiTreeUtil.getParentOfType(annotation, PsiClass.class, true);
        if (owner == null) {
            return;
        }
        PsiField field = owner.findFieldByName(name, true);
        if (field == null) {
            report(holder, value, "Lombok cannot find a field named '" + name + "' here; toBuilder() will not compile");
        }
    }

    private static boolean matches(PsiMethod @NotNull [] candidates,
                                   @NotNull java.util.function.Predicate<PsiMethod> predicate) {
        for (PsiMethod candidate : candidates) {
            if (predicate.test(candidate)) {
                return true;
            }
        }
        return false;
    }

    /** {@code isStatic = true} 를 적었는가. 상수 참조처럼 리터럴이 아니면 검사하지 않는다(기본값으로 본다). */
    private static boolean isStaticRequested(@NotNull PsiAnnotation annotation) {
        PsiAnnotationMemberValue value = annotation.findDeclaredAttributeValue(ATTR_IS_STATIC);
        return value instanceof PsiLiteralExpression literal && Boolean.TRUE.equals(literal.getValue());
    }

    private static @Nullable String literalString(@Nullable PsiAnnotationMemberValue value) {
        return value instanceof PsiLiteralExpression literal && literal.getValue() instanceof String text
            ? text
            : null;
    }

    private static void report(@NotNull ProblemsHolder holder,
                               @NotNull PsiElement anchor,
                               @NotNull String message) {
        holder.registerProblem(anchor, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
    }
}
