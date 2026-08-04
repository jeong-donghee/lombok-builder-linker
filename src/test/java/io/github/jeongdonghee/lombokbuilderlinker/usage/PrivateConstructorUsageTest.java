package io.github.jeongdonghee.lombokbuilderlinker.usage;

import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.PsiSearchScopeUtil;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.usageView.UsageInfo;
import io.github.jeongdonghee.lombokbuilderlinker.LombokTestCase;

import java.util.Collection;

/**
 * 재현 테스트 — {@code @Builder} 가 <b>private</b> 생성자에 붙은 경우.
 *
 * <p>{@code RealLombokUsageTest} 는 public 생성자만 태운다. 실사용(AlmHstEnt)에서 private 생성자로는
 * 사용처가 안 나온다는 보고가 있어 갈라지는지 확인한다.
 */
public class PrivateConstructorUsageTest extends LombokTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        myFixture.addFileToProject("PrivateCase.java", """
            import lombok.Builder;
            public class PrivateCase {
                private final String channelName;

                @Builder(builderMethodName = "historyChannelBuilder")
                private PrivateCase(String channelName) {
                    this.channelName = channelName;
                }
            }
            """);
        myFixture.addFileToProject("PrivateCaller.java", """
            public class PrivateCaller {
                void use() {
                    PrivateCase built = PrivateCase.historyChannelBuilder()
                        .channelName("history-1")
                        .build();
                }
            }
            """);
    }

    /**
     * 뿌리 확인 — private 생성자의 검색 범위가 클래스 본문 밖까지 닿는가.
     *
     * <p>고치기 전에는 {@code LocalSearchScope: [PsiClass:PrivateCase]} 였다. 그 좁은 범위가
     * {@code getEffectiveSearchScope()} 로 검색기까지 흘러들어 호출부를 못 찾은 것이 원인이었다.
     * {@code BuilderMemberUseScopeEnlarger} 가 진입 메서드만큼 범위를 넓혀 준다.
     */
    public void testUseScopeOfPrivateConstructorReachesCallerFile() {
        PsiMethod constructor = privateCase().getConstructors()[0];
        SearchScope useScope = PsiSearchHelper.getInstance(getProject()).getUseScope(constructor);

        PsiFile caller = myFixture.findFileInTempDir("PrivateCaller.java") == null
            ? null
            : PsiManager.getInstance(getProject())
                .findFile(myFixture.findFileInTempDir("PrivateCaller.java"));
        assertNotNull("PrivateCaller.java 를 찾지 못했다", caller);
        assertTrue("private 생성자의 검색 범위가 호출부 파일까지 닿아야 한다: " + useScope,
            PsiSearchScopeUtil.isInScope(useScope, caller));
    }

    public void testReferencesToPrivateBuilderConstructorIncludeCallSites() {
        PsiMethod constructor = privateCase().getConstructors()[0];

        Collection<PsiReference> references = ReferencesSearch.search(constructor, scope()).findAll();
        boolean fromCaller = references.stream().anyMatch(reference ->
            "PrivateCaller.java".equals(reference.getElement().getContainingFile().getName()));
        assertTrue("PrivateCaller.java 의 호출이 잡혀야 한다: " + references.size() + "건", fromCaller);
    }

    public void testFindUsagesOnPrivateConstructorShowsBuilderCallSites() {
        PsiMethod constructor = privateCase().getConstructors()[0];

        Collection<UsageInfo> usages = myFixture.findUsages(constructor);
        boolean fromCaller = usages.stream().anyMatch(usage -> {
            PsiElement element = usage.getElement();
            return element != null
                && "PrivateCaller.java".equals(element.getContainingFile().getName());
        });
        assertTrue("Find Usages 에 PrivateCaller.java 의 호출이 나와야 한다: " + usages, fromCaller);
    }

    private PsiClass privateCase() {
        PsiClass found = JavaPsiFacade.getInstance(getProject()).findClass("PrivateCase", scope());
        assertNotNull("PrivateCase 를 찾지 못했다", found);
        return found;
    }

    private GlobalSearchScope scope() {
        return GlobalSearchScope.projectScope(getProject());
    }
}
