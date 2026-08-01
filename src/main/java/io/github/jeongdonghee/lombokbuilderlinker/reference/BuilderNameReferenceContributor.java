package io.github.jeongdonghee.lombokbuilderlinker.reference;

import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.PsiReferenceRegistrar;
import com.intellij.util.ProcessingContext;
import io.github.jeongdonghee.lombokbuilderlinker.model.LombokAnnotations;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Lombok 애노테이션 안의 <b>이름 문자열</b>에 참조를 심는다.
 *
 * <p>실측(2026-07-31, {@code builder-zoo} 21개 케이스)에서 이 여섯 자리 전부 참조가 없었다:
 * {@code builderMethodName} · {@code buildMethodName} · {@code builderClassName} ·
 * {@code setterPrefix} · {@code ObtainVia(method)} · {@code ObtainVia(field)}.
 * IntelliJ 는 이 문자열들을 그냥 글자로만 본다.
 *
 * <p>등록 패턴을 "애노테이션 속성 자리의 문자열"로 좁혀둔다. 모든 문자열 리터럴마다 불리면
 * 편집기 응답성에 영향이 가기 때문이다.
 */
public final class BuilderNameReferenceContributor extends PsiReferenceContributor {

    @Override
    public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(PsiLiteralExpression.class).withParent(PsiNameValuePair.class),
            new BuilderNameReferenceProvider());

    }

    private static final class BuilderNameReferenceProvider extends PsiReferenceProvider {

        @Override
        public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element,
                                                              @NotNull ProcessingContext context) {
            AnnotationAttribute attribute = AnnotationAttribute.of(element);
            if (attribute == null || attribute.value().isEmpty()) {
                // 빈 문자열은 "만들지 말라"는 뜻이므로 가리킬 대상이 없다.
                return PsiReference.EMPTY_ARRAY;
            }
            LombokMemberReference.Kind kind = kindOf(attribute);
            if (kind == null) {
                return PsiReference.EMPTY_ARRAY;
            }
            return new PsiReference[]{
                new LombokMemberReference((PsiLiteralExpression) element, kind)
            };
        }

        private static @Nullable LombokMemberReference.Kind kindOf(@NotNull AnnotationAttribute attribute) {
            String qualifiedName = LombokAnnotations.qualifiedName(attribute.annotation());
            String attributeName = attribute.attributeName();

            if (LombokAnnotations.isBuilderAnnotation(qualifiedName)) {
                return switch (attributeName) {
                    case LombokAnnotations.ATTR_BUILDER_METHOD_NAME -> LombokMemberReference.Kind.BUILDER_METHOD;
                    case LombokAnnotations.ATTR_BUILD_METHOD_NAME -> LombokMemberReference.Kind.BUILD_METHOD;
                    case LombokAnnotations.ATTR_BUILDER_CLASS_NAME -> LombokMemberReference.Kind.BUILDER_CLASS;
                    case LombokAnnotations.ATTR_SETTER_PREFIX -> LombokMemberReference.Kind.SETTER_PREFIX;
                    default -> null;
                };
            }
            if (LombokAnnotations.OBTAIN_VIA.equals(qualifiedName)) {
                return switch (attributeName) {
                    case LombokAnnotations.ATTR_VIA_METHOD -> LombokMemberReference.Kind.VIA_METHOD;
                    case LombokAnnotations.ATTR_VIA_FIELD -> LombokMemberReference.Kind.VIA_FIELD;
                    default -> null;
                };
            }
            return null;
        }
    }
}
