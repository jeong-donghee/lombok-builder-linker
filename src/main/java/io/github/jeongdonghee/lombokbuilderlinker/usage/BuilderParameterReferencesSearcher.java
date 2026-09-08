package io.github.jeongdonghee.lombokbuilderlinker.usage;

import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

/**
 * {@code @Builder} 가 붙은 생성자·메서드의 파라미터를 찾을 때, 그 파라미터가 만들어낸 세터의
 * 호출부를 사용처로 함께 보고한다.
 *
 * <p>메서드 때와 달리 {@code methodReferencesSearch} 에는 등록하지 않는다 — 찾는 대상이 메서드가
 * 아니라 파라미터라서 그 경로를 타지 않는다. 실측으로 확인한 것: 파라미터는
 * {@code findUsages} 결과와 {@code ReferencesSearch} 결과가 1:1로 같다.
 *
 * <p>이 경로 하나로 <b>Find Usages 와 ⇧F6 이 동시에</b> 살아난다. 파라미터 이름 변경은
 * {@code ReferencesSearch} 로 사용처를 모아 각 참조의 {@code handleElementRename} 을 부르는데,
 * 여기서 보고하는 참조가 그 호출을 받아 세터 이름을 다시 쓴다({@code BuilderSetterCallSites}).
 */
public final class BuilderParameterReferencesSearcher
    extends QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters> {

    public BuilderParameterReferencesSearcher() {
        super(true); // 읽기 액션 안에서 실행
    }

    @Override
    public void processQuery(@NotNull ReferencesSearch.SearchParameters parameters,
                             @NotNull Processor<? super PsiReference> consumer) {
        PsiElement searched = parameters.getElementToSearch();
        if (searched instanceof PsiParameter parameter) {
            BuilderSetterCallSites.report(parameter, parameters.getEffectiveSearchScope(), consumer);
        }
    }
}
