package io.github.jeongdonghee.lombokbuilderlinker.usage;

import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.usageView.UsageInfo;
import io.github.jeongdonghee.lombokbuilderlinker.LombokTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * {@code record} 에 {@code @Builder} 를 붙인 경우를 못 박는다.
 *
 * <p>실측(2026-08-04, {@code builder-zoo} 의 R 그룹)에서 record 는 <b>이미 전부 동작했다</b>.
 * 코드를 고칠 게 없었던 이유는 {@link io.github.jeongdonghee.lombokbuilderlinker.model.BuilderTarget}
 * 이 "클래스냐 메서드냐"만 묻기 때문이다 — record 는 PSI 에서 {@code PsiClass} 이고 compact
 * constructor 는 평범한 {@code PsiMethod} 라 기존 분기에 그대로 얹힌다.
 *
 * <p>그래서 이 테스트는 버그를 고치려고 쓴 게 아니라 <b>그 사실을 고정</b>하려고 쓴다. 지금은
 * 우연히 맞는 상태이고, {@code BuilderTarget} 을 손대다 record 가 조용히 빠져도 나머지 테스트는
 * 전부 통과한다. 픽스처가 한 종류만 대표해서 생긴 구멍은 {@code PrivateConstructorUsageTest} 에서
 * 한 번 겪었다.
 *
 * <p>{@code @Builder} 가 <b>메서드에 붙는</b> 두 갈래를 모두 태운다 — 생성자(만드는 타입 = 담은
 * 클래스)와 static 팩터리(만드는 타입 = 반환형). 그 분기가 {@code BuilderTarget#from} 에 있다.
 *
 * <p>record 에는 private 생성자 케이스가 없다. canonical 생성자는 record 자신보다 좁은 접근을
 * 가질 수 없어서({@code public record} 면 canonical 생성자도 {@code public}), private 이 원인이던
 * 문제는 이 자리에서 생기지 않는다.
 */
public class RecordBuilderUsageTest extends LombokTestCase {

    private final BuilderImplicitUsageProvider provider = new BuilderImplicitUsageProvider();

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        myFixture.addFileToProject("RecCompact.java", """
            import lombok.Builder;
            public record RecCompact(String name, int count) {
                @Builder(builderMethodName = "recCompactBuilder")
                public RecCompact {
                    if (count < 0) {
                        throw new IllegalArgumentException("count");
                    }
                }
            }
            """);
        myFixture.addFileToProject("RecCanonical.java", """
            import lombok.Builder;
            public record RecCanonical(String name, int count) {
                @Builder(builderMethodName = "recCanonicalBuilder")
                public RecCanonical(String name, int count) {
                    this.name = name;
                    this.count = count;
                }
            }
            """);
        myFixture.addFileToProject("RecFactory.java", """
            import lombok.Builder;
            public record RecFactory(String name, int count) {
                @Builder(builderMethodName = "recFactoryBuilder")
                public static RecFactory of(String name, int count) {
                    return new RecFactory(name, count);
                }
            }
            """);
        myFixture.addFileToProject("RecObtainVia.java", """
            import lombok.Builder;
            @Builder(builderMethodName = "recObtainViaBuilder", toBuilder = true)
            public record RecObtainVia(String name, @Builder.ObtainVia(method = "doubledCount") int count) {
                public int doubledCount() {
                    return count * 2;
                }
            }
            """);
        myFixture.addFileToProject("RecordCaller.java", """
            public class RecordCaller {
                void use() {
                    RecCompact a = RecCompact.recCompactBuilder().name("a").count(1).build();
                    RecCanonical b = RecCanonical.recCanonicalBuilder().name("b").count(2).build();
                    RecFactory c = RecFactory.recFactoryBuilder().name("c").count(3).build();
                }
            }
            """);
    }

    /** 전제 확인 — Lombok 이 record 에도 진입 메서드를 증강하는가. 여기가 깨지면 나머지는 무의미하다. */
    public void testLombokAugmentsRecords() {
        assertEquals("compact ctor 쪽 진입 메서드가 없다",
            1, recordClass("RecCompact").findMethodsByName("recCompactBuilder", false).length);
        assertEquals("canonical ctor 쪽 진입 메서드가 없다",
            1, recordClass("RecCanonical").findMethodsByName("recCanonicalBuilder", false).length);
        assertEquals("static 팩터리 쪽 진입 메서드가 없다",
            1, recordClass("RecFactory").findMethodsByName("recFactoryBuilder", false).length);
    }

    /** 이 그룹의 핵심 — compact constructor. 일반 클래스에서 "생성자에 붙으면 깨진다"였던 자리에 대응한다. */
    public void testFindUsagesOnCompactConstructorShowsBuilderCallSites() {
        assertCallerFound(constructorOf("RecCompact"));
    }

    /** 파라미터를 다 적은 canonical constructor. Lombok 문서가 권하는 모양이다. */
    public void testFindUsagesOnCanonicalConstructorShowsBuilderCallSites() {
        assertCallerFound(constructorOf("RecCanonical"));
    }

    /** static 팩터리 — 만들어지는 타입이 담은 클래스가 아니라 <b>반환형</b>으로 정해지는 갈래. */
    public void testFindUsagesOnStaticFactoryShowsBuilderCallSites() {
        assertCallerFound(recordClass("RecFactory").findMethodsByName("of", false)[0]);
    }

    /** 선언이 회색(unused)으로 뜨지 않아야 한다 — 사용처를 찾는 것과는 별개의 확장점이다. */
    public void testRecordBuilderMembersAreImplicitlyUsed() {
        assertTrue("compact ctor 가 회색으로 뜬다", provider.isImplicitUsage(constructorOf("RecCompact")));
        assertTrue("canonical ctor 가 회색으로 뜬다", provider.isImplicitUsage(constructorOf("RecCanonical")));
        assertTrue("static 팩터리가 회색으로 뜬다",
            provider.isImplicitUsage(recordClass("RecFactory").findMethodsByName("of", false)[0]));
    }

    /**
     * {@code @Builder.ObtainVia(method = ...)} 가 <b>record 컴포넌트</b>에 붙은 경우.
     *
     * <p>CLAUDE.md 가 가장 위험하다고 꼽은 자리다 — 그 메서드는 이름 문자열로만 참조되므로,
     * IDE 가 모르면 회색으로 뜨고 Safe Delete 가 경고 없이 지운다. record 헤더의 컴포넌트에
     * 애노테이션이 붙는 모양은 필드에 붙는 것과 PSI 가 다르므로 따로 태운다.
     */
    public void testMethodNamedByObtainViaOnRecordComponentIsImplicitlyUsed() {
        PsiMethod named = recordClass("RecObtainVia").findMethodsByName("doubledCount", false)[0];
        assertTrue("ObtainVia 가 이름으로 가리키는 메서드가 회색으로 뜬다", provider.isImplicitUsage(named));
    }

    private void assertCallerFound(@NotNull PsiMethod declaration) {
        Collection<UsageInfo> usages = myFixture.findUsages(declaration);
        boolean fromCaller = usages.stream().anyMatch(usage -> {
            PsiElement element = usage.getElement();
            return element != null && "RecordCaller.java".equals(element.getContainingFile().getName());
        });
        assertTrue("RecordCaller.java 의 빌더 호출이 사용처로 나와야 한다: " + usages, fromCaller);
    }

    private PsiMethod constructorOf(@NotNull String recordName) {
        PsiMethod[] constructors = recordClass(recordName).getConstructors();
        assertEquals(recordName + " 의 생성자를 하나 기대했다: " + constructors.length, 1, constructors.length);
        return constructors[0];
    }

    private PsiClass recordClass(@NotNull String name) {
        PsiClass found = JavaPsiFacade.getInstance(getProject())
            .findClass(name, GlobalSearchScope.projectScope(getProject()));
        assertNotNull(name + " 을 찾지 못했다", found);
        assertTrue(name + " 이 record 가 아니다", found.isRecord());
        return found;
    }
}
