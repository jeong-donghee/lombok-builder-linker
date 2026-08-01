package io.github.jeongdonghee.lombokbuilderlinker.reference;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.util.PsiTreeUtil;
import io.github.jeongdonghee.lombokbuilderlinker.model.LombokAnnotations;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@code @Builder} 의 이름을 바꿀 때 그 빌더를 쓰는 <b>호출부 전부</b>를 함께 고치기 위한 조각들.
 *
 * <p>왜 필요한가: 이 이름은 생성될 멤버의 이름을 <b>정하는</b> 값이다. 바꾸면 Lombok 은 새 이름으로
 * 멤버를 만들고, 옛 이름을 부르던 호출부는 전부 해석되지 않는 상태가 된다. 그런데 평범한 이름 변경
 * 리팩터링은 여기서 아무것도 하지 못한다 — 이름이 가리키는 대상이 소스 없는 합성 멤버여서 플랫폼이
 * 이름을 바꿀 실체가 없기 때문이다. 그래서 애노테이션을 기준점으로 삼아 직접 고친다.
 *
 * <p>역할 분담: 대상 판정과 호출부 수집·수정이 여기 있고, 편집기 안에서의 실제 편집은
 * {@link BuilderNameInplaceRename}, ⇧F6 진입은 {@link BuilderNameRenameHandler} 가 맡는다.
 */
final class BuilderNameRename {

    private BuilderNameRename() {}

    /** 캐럿 위치의 요소에서 이름 변경 대상이 될 문자열을 찾는다. */
    static @Nullable PsiLiteralExpression renamableLiteralAt(@Nullable PsiElement element) {
        PsiLiteralExpression literal = PsiTreeUtil.getParentOfType(element, PsiLiteralExpression.class, false);
        return referenceOf(literal) == null ? null : literal;
    }

    /**
     * 이름 변경을 지원하는 참조인가.
     *
     * <p>{@code setterPrefix} 도 대상이다. 다만 성격이 다르다 — 접두사를 바꾸면 호출부마다 이름이
     * 달라진다({@code withName} → {@code setName}, {@code withCount} → {@code setCount}).
     * 그래서 호출부를 모을 때 <b>접미사</b>를 함께 들고 다닌다({@link CallSite}).
     */
    private static @Nullable LombokMemberReference referenceOf(@Nullable PsiLiteralExpression literal) {
        if (literal == null) {
            return null;
        }
        for (PsiReference reference : literal.getReferences()) {
            if (reference instanceof LombokMemberReference member && member.pointsToGeneratedMember()) {
                return member;
            }
        }
        return null;
    }

    /**
     * 고쳐야 할 호출부 하나.
     *
     * @param element 호출부 요소
     * @param suffix  새 이름에서 <b>바뀌지 않는 뒷부분</b>. 보통은 빈 문자열이고,
     *                {@code setterPrefix} 일 때만 {@code Name} · {@code Count} 처럼 값이 있다.
     *                최종 이름은 {@code 새로 친 값 + suffix} 다.
     */
    record CallSite(@NotNull PsiElement element, @NotNull String suffix) {}

    /** 현재 이름. 인라인 편집의 초기값으로 쓴다. */
    static @Nullable String currentName(@Nullable PsiLiteralExpression literal) {
        return literal != null && literal.getValue() instanceof String text ? text : null;
    }

    /**
     * 인라인 편집을 시작하기 <b>전에</b> 호출부를 모아 둔다.
     *
     * <p>순서가 중요하다: 이름을 먼저 바꾸면 Lombok 이 새 이름으로 멤버를 다시 만들어내고,
     * 그 시점에는 옛 이름을 부르던 호출부를 더 이상 찾을 수 없다.
     */
    static List<CallSite> callSites(@Nullable PsiLiteralExpression literal) {
        AnnotationAttribute attribute = AnnotationAttribute.of(literal);
        if (referenceOf(literal) == null || attribute == null) {
            return List.of();
        }
        return collectCallSites(attribute.annotation(), attribute.attributeName(), attribute.value());
    }

    /**
     * 모아 둔 호출부를 고친다. 쓰기 액션 안에서 불러야 한다.
     *
     * <p>이름은 자리마다 다를 수 있다 — <b>새로 친 값 + 그 자리의 접미사</b>. 접두사가 아닌 보통의
     * 이름 변경에서는 접미사가 비어 있어 모두 같은 이름이 된다.
     */
    static void renameCallSites(@NotNull List<CapturedCallSite> callSites, @NotNull String newBaseName) {
        for (CapturedCallSite callSite : callSites) {
            PsiElement element = callSite.pointer().getElement();
            if (element == null || !element.isValid()) {
                continue; // 편집 중 사라진 자리는 건너뛴다.
            }
            String newName = newBaseName + callSite.suffix();
            for (PsiReference reference : element.getReferences()) {
                reference.handleElementRename(newName);
            }
        }
    }

    /** 편집을 시작하기 전에 붙잡아 둔 호출부. */
    record CapturedCallSite(@NotNull SmartPsiElementPointer<?> pointer, @NotNull String suffix) {}

    private static List<CallSite> collectCallSites(@NotNull PsiAnnotation annotation,
                                                   @NotNull String attributeName,
                                                   @NotNull String name) {
        GlobalSearchScope scope = GlobalSearchScope.projectScope(annotation.getProject());
        Set<PsiReference> found = new LinkedHashSet<>();
        Map<PsiReference, String> suffixes = new LinkedHashMap<>();

        // 합성 멤버는 참조 해석으로 노출하지 않으므로 전용 조회를 쓴다(LombokMemberReference 주석 참고).
        for (PsiElement generated : LombokGeneratedMembers.of(annotation, attributeName, name)) {
            String suffix = suffixOf(generated, name);
            ReferencesSearch.search(generated, scope, false).forEach(candidate -> {
                // 이름을 정하는 자리(애노테이션 문자열)는 호출부가 아니다 — 그쪽은 따로 고친다.
                if (!LombokAnnotations.isInsideLombokAnnotation(candidate.getElement())) {
                    found.add(candidate);
                    suffixes.put(candidate, suffix);
                }
                return true;
            });
        }
        return found.stream()
            .map(reference -> new CallSite(reference.getElement(), suffixes.getOrDefault(reference, "")))
            .toList();
    }

    /**
     * 멤버 이름에서 <b>문자열이 정하지 않는 뒷부분</b>. 접두사일 때만 값이 있다
     * ({@code with} 가 만든 {@code withName} → {@code Name}).
     */
    private static String suffixOf(@NotNull PsiElement generated, @NotNull String declaredValue) {
        if (!(generated instanceof PsiNamedElement named)) {
            return "";
        }
        String memberName = named.getName();
        if (memberName == null || memberName.equals(declaredValue) || !memberName.startsWith(declaredValue)) {
            return "";
        }
        return memberName.substring(declaredValue.length());
    }
}
