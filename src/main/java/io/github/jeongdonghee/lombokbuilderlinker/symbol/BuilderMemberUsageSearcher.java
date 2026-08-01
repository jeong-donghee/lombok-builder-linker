package io.github.jeongdonghee.lombokbuilderlinker.symbol;

import com.intellij.find.usages.api.PsiUsage;
import com.intellij.find.usages.api.Usage;
import com.intellij.find.usages.api.UsageSearchParameters;
import com.intellij.find.usages.api.UsageSearcher;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.Query;
import io.github.jeongdonghee.lombokbuilderlinker.model.LombokAnnotations;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 사용처 팝업·Find Usages 에 <b>내용을 채워 넣는</b> 조각.
 *
 * <p>이게 없으면 팝업은 뜨지만 "No usages found in Project Files" 로 비어 있다. 문자열이 선언이라고
 * 알리는 것({@link BuilderNameDeclarationProvider})과 그 선언의 사용처를 <b>찾아오는</b> 것은 서로
 * 다른 일이기 때문이다. 팝업은 {@code SearchService.searchParameters(UsageSearchParameters)} 를
 * 타고, 그 경로는 {@code searcher} 확장점에 등록된 검색기에게만 묻는다(플랫폼 바이트코드 확인).
 *
 * <p><b>왜 심볼 참조 검색이 아니라 PSI 참조 검색인가.</b> 이 심볼이 가리키는 것은 결국 Lombok 이
 * 만들어 둔 <b>진짜 PSI 멤버</b>다. 그 멤버의 참조를 {@link ReferencesSearch} 로 찾으면 플랫폼의
 * 자바 검색이 그대로 일해 준다 — 별도의 심볼 참조 검색기를 등록할 필요가 없다.
 * <p>심볼 참조 검색 쪽 API({@code CodeReferenceSearcher} ·
 * {@code SearchService.searchPsiSymbolReferences})는 쓰지 않는다 — <b>2025.1 에서 없어졌다</b>
 * (plugin verifier 로 확인). {@link ReferencesSearch} 는 옛 IDE 와 새 IDE 에서 그대로 동작한다.
 */
public final class BuilderMemberUsageSearcher implements UsageSearcher {

    @Override
    public @NotNull Collection<? extends Query<? extends Usage>> collectSearchRequests(
        @NotNull UsageSearchParameters parameters) {

        if (!(parameters.getTarget() instanceof BuilderMemberSearchTarget target)) {
            return List.of();
        }
        BuilderMemberSymbol symbol = target.symbol();
        PsiClass host = findClass(parameters.getProject(), symbol.hostClassName());
        if (host == null) {
            // Lombok 지원이 꺼져 있으면 멤버가 아예 없다 — 조용히 아무것도 찾지 않는다.
            return List.of();
        }
        List<Query<? extends Usage>> queries = new ArrayList<>();
        for (PsiElement member : generatedMembers(host, symbol)) {
            queries.add(usagesOf(member, parameters.getSearchScope()));
        }
        return queries;
    }

    /**
     * 이 심볼이 가리키는, Lombok 이 만들어 둔 멤버들.
     *
     * <p>{@code setterPrefix} 만 여럿이다 — 접두사 하나가 세터 여러 개를 만든다. 나머지는 하나다.
     */
    private static List<PsiElement> generatedMembers(@NotNull PsiClass host,
                                                     @NotNull BuilderMemberSymbol symbol) {
        String name = symbol.memberName();
        return switch (symbol.kind()) {
            case BUILDER_METHOD, BUILD_METHOD -> List.of((PsiElement[]) host.findMethodsByName(name, false));
            case BUILDER_CLASS -> {
                PsiClass found = host.findInnerClassByName(name, false);
                yield found == null ? List.of() : List.of(found);
            }
            case SETTER_PREFIX -> {
                List<PsiElement> setters = new ArrayList<>();
                for (PsiMethod setter : host.getMethods()) {
                    String setterName = setter.getName();
                    if (setterName.length() > name.length() && setterName.startsWith(name)) {
                        setters.add(setter);
                    }
                }
                yield setters;
            }
        };
    }

    /**
     * 한 멤버의 호출부.
     *
     * <p>이름을 <b>정하는</b> 자리(애노테이션 문자열)는 걸러낸다 — 이 플러그인이 그 문자열에 심어 둔
     * 참조도 같은 멤버를 가리키므로, 그대로 두면 선언이 자기 자신의 사용처로 목록에 들어온다.
     *
     * <p>참고: 찾아낸 사용처가 하나뿐이거나 전부 <b>한 줄에</b> 있으면 플랫폼은 목록 대신 그 자리로
     * 이동한다({@code ShowUsagesAction}). 빌더는 한 줄 체인으로 쓰므로 흔한 일이다 — 검색이 잘못된
     * 것이 아니다.
     */
    private static Query<? extends Usage> usagesOf(@NotNull PsiElement member, @NotNull SearchScope scope) {
        return ReferencesSearch.search(member, scope, false)
            .filtering(reference -> !LombokAnnotations.isInsideLombokAnnotation(reference.getElement()))
            .mapping(reference -> PsiUsage.textUsage(reference.getElement(), reference.getRangeInElement()));
    }

    /**
     * 정규화된 이름으로 클래스를 찾는다. 빌더 클래스는 <b>중첩 클래스</b>이고 Lombok 이 만들어낸
     * 것이라, 인덱스 조회가 비면 바깥 클래스를 거쳐 이름으로 찾아 들어간다.
     */
    private static @Nullable PsiClass findClass(@NotNull Project project, @NotNull String qualifiedName) {
        GlobalSearchScope scope = GlobalSearchScope.allScope(project);
        PsiClass found = JavaPsiFacade.getInstance(project).findClass(qualifiedName, scope);
        if (found != null) {
            return found;
        }
        int lastDot = qualifiedName.lastIndexOf('.');
        if (lastDot < 0) {
            return null;
        }
        PsiClass outer = findClass(project, qualifiedName.substring(0, lastDot));
        return outer == null ? null : outer.findInnerClassByName(qualifiedName.substring(lastDot + 1), false);
    }
}
