package io.github.jeongdonghee.lombokbuilderlinker.reference;

import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementResolveResult;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.util.PsiTreeUtil;
import io.github.jeongdonghee.lombokbuilderlinker.model.LombokAnnotations;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Lombok 애노테이션의 이름 문자열에서 그 이름이 가리키는 멤버로 가는 참조.
 *
 * <p>참조 하나를 심으면 ⌘+Click(이동) · Find Usages · Rename · Safe Delete 가 <b>동시에</b>
 * 동작한다. 넷 다 같은 {@code PsiReference} 기계를 쓰기 때문이다. 그래서 이 클래스가
 * 이 플러그인에서 가장 값이 큰 조각이다.
 *
 * <p>{@code soft = true} 로 둔다. Lombok 플러그인이 꺼져 있거나 이름이 실제 멤버와 맞지 않을 때
 * 빨간 오류로 코드를 어지럽히는 대신 조용히 아무것도 하지 않는 편이 안전하다.
 *
 * <p><b>해석 결과를 두 갈래로 나눈다.</b> {@code ObtainVia} 처럼 <b>직접 쓴</b> 멤버를 가리킬 때는
 * {@link #multiResolve} 로 정상 해석한다 — 그래야 그 멤버의 이름 변경·안전 삭제가 이 문자열을 본다.
 * 반면 Lombok 이 <b>만들어낸</b> 멤버를 가리킬 때는 해석 결과를 비워 두고
 * {@link #generatedTargets()} 로만 얻는다.
 *
 * <p>왜 그렇게 하는가: Lombok 플러그인의 {@code LombokElementRenameVetoHandler} 는 캐럿의 해석
 * 결과가 자기네 합성 요소({@code LombokLightMethodBuilder} 등)면 이름 변경을 가로채겠다고 손을 든다.
 * 우리 처리기도 손을 들기 때문에 플랫폼이 <b>처리기 선택 팝업</b>을 띄웠다
 * ({@code RenameHandlerRegistry} 에는 우선순위나 거부 수단이 없다). 합성 멤버를 해석 결과로
 * 내놓지 않으면 Lombok 쪽 조건이 성립하지 않아 팝업 없이 우리 처리기로 바로 들어간다.
 * 합성 멤버는 소스가 없어 어차피 이동·이름 변경의 실체가 아니므로 잃는 것이 없다.
 * 이 계약은 {@code RenameHandlerAmbiguityTest} 가 지킨다.
 */
final class LombokMemberReference extends PsiReferenceBase.Poly<PsiLiteralExpression> {

    /** 이 문자열이 무엇을 가리키는 이름인지. */
    enum Kind {
        /** {@code builderMethodName} — 생성된 빌더 진입 메서드. */
        BUILDER_METHOD,
        /** {@code buildMethodName} — 생성된 build 메서드. */
        BUILD_METHOD,
        /** {@code builderClassName} — 생성된 빌더 클래스. */
        BUILDER_CLASS,
        /** {@code setterPrefix} — 접두사 하나가 생성된 세터 여러 개에 대응한다(1:N). */
        SETTER_PREFIX,
        /** {@code @Builder.ObtainVia(method = ...)} — <b>직접 쓴</b> 메서드. */
        VIA_METHOD,
        /** {@code @Builder.ObtainVia(field = ...)} — <b>직접 쓴</b> 필드. */
        VIA_FIELD
    }

    private final Kind kind;

    /**
     * 이 이름이 <b>Lombok 이 만들어낸</b> 멤버를 가리키는가.
     *
     * <p>그런 멤버는 소스가 없어서 "선언으로 가기"의 착지점이 없다 — Lombok 은 합성 멤버의
     * navigation element 를 애노테이션으로 잡아두므로, 호출부에서 ⌘+Click 하면 {@code @Builder}
     * 앞으로 간다. 이름 문자열 쪽은 그 자체가 선언이므로 플랫폼이 사용처 팝업을 띄운다
     * ({@code symbol} 패키지).
     *
     * <p>{@code ObtainVia} 는 반대로 <b>직접 쓴</b> 멤버를 가리키므로 그냥 이동하면 된다.
     */
    boolean pointsToGeneratedMember() {
        return kind == Kind.BUILDER_METHOD
            || kind == Kind.BUILD_METHOD
            || kind == Kind.BUILDER_CLASS
            || kind == Kind.SETTER_PREFIX;
    }

    LombokMemberReference(@NotNull PsiLiteralExpression element, @NotNull Kind kind) {
        super(element, ElementManipulators.getValueTextRange(element), true);
        this.kind = kind;
    }

    /**
     * <b>직접 쓴</b> 멤버를 가리킬 때만 해석 결과를 내놓는다.
     *
     * <p>합성 멤버(→ {@link #generatedTargets()})는 일부러 비워 둔다. 그 이유는 클래스 주석에 있다.
     */
    @Override
    public ResolveResult @NotNull [] multiResolve(boolean incompleteCode) {
        if (pointsToGeneratedMember()) {
            return ResolveResult.EMPTY_ARRAY;
        }
        AnnotationAttribute attribute = AnnotationAttribute.of(getElement());
        if (attribute == null) {
            return ResolveResult.EMPTY_ARRAY;
        }
        List<PsiElement> targets = switch (kind) {
            case VIA_METHOD -> viaMethods(attribute);
            case VIA_FIELD -> viaField(attribute);
            // 합성 멤버는 해석에 노출하지 않는다.
            case BUILDER_METHOD, BUILD_METHOD, BUILDER_CLASS, SETTER_PREFIX -> List.of();
        };
        return PsiElementResolveResult.createResults(targets);
    }

    /**
     * Lombok 이 만들어 둔, 이 이름이 가리키는 멤버들.
     *
     * <p>해석({@link #multiResolve})에 노출하지 않으므로 이 경로로만 얻는다. 사용처 수집과
     * 이름 변경 시 호출부 수집이 이걸 쓴다. 조회 규칙 자체는 {@link LombokGeneratedMembers} 에 있다 —
     * 호출부에서 거꾸로 올라오는 판정과 같은 규칙을 써야 어긋나지 않는다.
     */
    @NotNull List<PsiElement> generatedTargets() {
        AnnotationAttribute attribute = AnnotationAttribute.of(getElement());
        if (attribute == null || !pointsToGeneratedMember()) {
            return List.of();
        }
        return LombokGeneratedMembers.of(attribute.annotation(), attribute.attributeName(), attribute.value());
    }

    // ---------- ObtainVia: 직접 쓴 멤버를 가리킨다 (Rename 안전성이 걸린 자리) ----------

    private List<PsiElement> viaMethods(@NotNull AnnotationAttribute attribute) {
        PsiClass owner = enclosingClass(attribute);
        if (owner == null) {
            return List.of();
        }
        // 오버로드가 있을 수 있어 다중 해석. Lombok 은 무인자 메서드를 호출하지만
        // 이름이 같은 것 전부를 대상으로 잡아야 Rename 이 하나도 놓치지 않는다.
        return byName(owner.findMethodsByName(attribute.value(), true));
    }

    private List<PsiElement> viaField(@NotNull AnnotationAttribute attribute) {
        PsiClass owner = enclosingClass(attribute);
        if (owner == null) {
            return List.of();
        }
        PsiField field = owner.findFieldByName(attribute.value(), true);
        return field == null ? List.of() : List.of(field);
    }

    private PsiClass enclosingClass(@NotNull AnnotationAttribute attribute) {
        return PsiTreeUtil.getParentOfType(attribute.annotation(), PsiClass.class, true);
    }

    // ---------- 공통 ----------

    private static List<PsiElement> byName(PsiMethod @NotNull [] methods) {
        return methods.length == 0 ? List.of() : List.of((PsiElement[]) methods);
    }

    @Override
    public Object @NotNull [] getVariants() {
        // 자동완성이 의미 있는 자리만: ObtainVia 는 이미 존재하는 멤버를 가리킨다.
        AnnotationAttribute attribute = AnnotationAttribute.of(getElement());
        if (attribute == null) {
            return EMPTY_ARRAY;
        }
        PsiClass owner = enclosingClass(attribute);
        if (owner == null) {
            return EMPTY_ARRAY;
        }
        return switch (kind) {
            case VIA_METHOD -> owner.getMethods();
            case VIA_FIELD -> owner.getFields();
            default -> EMPTY_ARRAY;
        };
    }

    /** 이름이 바뀌면 문자열도 같이 바뀐다 — {@code LombokAnnotations} 의 기본값과 충돌하지 않게 그대로 쓴다. */
    @Override
    public @NotNull String getCanonicalText() {
        AnnotationAttribute attribute = AnnotationAttribute.of(getElement());
        return attribute == null ? LombokAnnotations.SUPPRESSED : attribute.value();
    }
}
