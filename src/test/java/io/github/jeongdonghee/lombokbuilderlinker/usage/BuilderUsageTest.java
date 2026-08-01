package io.github.jeongdonghee.lombokbuilderlinker.usage;

import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import io.github.jeongdonghee.lombokbuilderlinker.LombokStubs;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * "사용되지 않음" 오탐 억제(U2)와 빌더 호출부를 사용처로 보고하는 검색기(U1·F) 검증.
 */
public class BuilderUsageTest extends LightJavaCodeInsightFixtureTestCase {

    private final BuilderImplicitUsageProvider provider = new BuilderImplicitUsageProvider();

    @Override
    protected @NotNull LightProjectDescriptor getProjectDescriptor() {
        return JAVA_17;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        LombokStubs.add(myFixture);
    }

    // ---------- U2: 회색 처리 억제 ----------

    /** 생성자에 붙은 경우 — 실측 ★·P02b·P04. */
    public void testConstructorWithBuilderIsImplicitlyUsed() {
        PsiClass sample = configureClass("""
            import lombok.Builder;
            class Sample {
                private final String name;
                @Builder
                Sample(String name) { this.name = name; }
            }
            """);
        assertTrue("@Builder 생성자는 쓰이는 중으로 봐야 한다",
            provider.isImplicitUsage(sample.getConstructors()[0]));
    }

    /** static 메서드에 붙은 경우 — 실측 P05. */
    public void testStaticMethodWithBuilderIsImplicitlyUsed() {
        PsiClass sample = configureClass("""
            import lombok.Builder;
            class Sample {
                @Builder
                static Sample of(String name) { return null; }
            }
            """);
        assertTrue(provider.isImplicitUsage(sample.findMethodsByName("of", false)[0]));
    }

    /** instance 메서드에 붙은 경우 — 실측 P06. 어느 JetBrains 티켓에도 없는 케이스다. */
    public void testInstanceMethodWithBuilderIsImplicitlyUsed() {
        PsiClass sample = configureClass("""
            import lombok.Builder;
            class Sample {
                @Builder
                String repeat(String who, int times) { return null; }
            }
            """);
        assertTrue(provider.isImplicitUsage(sample.findMethodsByName("repeat", false)[0]));
    }

    /** {@code @SuperBuilder} 를 생성자에 쓴 경우도 같다. */
    public void testSuperBuilderConstructorIsImplicitlyUsed() {
        PsiClass sample = configureClass("""
            import lombok.experimental.SuperBuilder;
            class Sample {
                private final String name;
                @SuperBuilder
                Sample(String name) { this.name = name; }
            }
            """);
        assertTrue(provider.isImplicitUsage(sample.getConstructors()[0]));
    }

    /**
     * 클래스에 붙은 경우는 실측에서 4/4 정상이었으므로 손대지 않는다.
     * 필요 없는 곳까지 "쓰이는 중"이라고 우기면 진짜 죽은 코드를 못 잡는다.
     */
    public void testClassLevelBuilderIsNotMarked() {
        PsiClass sample = configureClass("""
            import lombok.Builder;
            @Builder
            class Sample { private String name; }
            """);
        assertFalse("클래스는 대상이 아니다", provider.isImplicitUsage(sample));
    }

    /** @Builder 가 없는 평범한 생성자는 당연히 대상이 아니다. */
    public void testPlainConstructorIsNotMarked() {
        PsiClass sample = configureClass("""
            class Sample {
                private final String name;
                Sample(String name) { this.name = name; }
            }
            """);
        assertFalse(provider.isImplicitUsage(sample.getConstructors()[0]));
    }

    // ---------- U2: ObtainVia 가 이름으로 가리키는 멤버 ----------

    /**
     * {@code ObtainVia(method = ...)} 가 가리키는 메서드는 회색이 되면 안 된다.
     *
     * <p>처음에는 이 자리를 범위에서 뺐다 — 애노테이션 문자열에 참조를 심었으니 회색도 풀릴 것이라고
     * 봤기 때문이다. 실제 IDE 에서 {@code computeLength()} 가 여전히 회색이어서 가정이 깨졌다.
     * 참조를 soft 로 두었고 unused 인스펙션은 그런 참조를 사용처로 세지 않는다.
     */
    public void testMethodNamedByObtainViaIsImplicitlyUsed() {
        PsiClass sample = configureClass("""
            import lombok.Builder;
            @Builder(toBuilder = true)
            class Sample {
                private String name;
                @Builder.ObtainVia(method = "computeLength")
                private int length;
                public int computeLength() { return name == null ? 0 : name.length(); }
            }
            """);
        assertTrue("ObtainVia 가 가리키는 메서드는 쓰이는 중으로 봐야 한다",
            provider.isImplicitUsage(sample.findMethodsByName("computeLength", false)[0]));
    }

    /** {@code ObtainVia(field = ...)} 가 가리키는 필드도 마찬가지 — 값을 읽어가므로 read 로도 표시한다. */
    public void testFieldNamedByObtainViaIsImplicitlyUsed() {
        PsiClass sample = configureClass("""
            import lombok.Builder;
            @Builder(toBuilder = true)
            class Sample {
                private String displayName;
                @Builder.ObtainVia(field = "displayName")
                private String alias;
            }
            """);
        PsiField field = sample.findFieldByName("displayName", false);
        assertNotNull(field);
        assertTrue(provider.isImplicitUsage(field));
        assertTrue(provider.isImplicitRead(field));
    }

    /**
     * 이름이 맞지 않는 멤버는 대상이 아니다.
     * 아무 메서드나 살려두면 진짜 죽은 코드를 못 잡게 되므로 이 경계가 중요하다.
     */
    public void testMemberNotNamedByObtainViaIsNotMarked() {
        PsiClass sample = configureClass("""
            import lombok.Builder;
            @Builder(toBuilder = true)
            class Sample {
                private String name;
                @Builder.ObtainVia(method = "computeLength")
                private int length;
                public int computeLength() { return 0; }
                public int somethingElse() { return 0; }
            }
            """);
        assertFalse("가리켜지지 않은 메서드는 그대로 죽은 코드로 봐야 한다",
            provider.isImplicitUsage(sample.findMethodsByName("somethingElse", false)[0]));
    }

    // ---------- U1 · F: 빌더 호출부를 사용처로 보고 ----------

    /**
     * 생성자에서 Find Usages 하면 {@code Sample.sampleBuilder()} 호출이 잡혀야 한다.
     *
     * <p>픽스처의 {@code build()} 는 일부러 생성자를 호출하지 않는다. Lombok 이 만드는 실제
     * {@code build()} 에는 소스가 없어서 참조가 존재하지 않는데, 그 상황을 그대로 재현한 것이다.
     * 따라서 이 검색기가 없으면 결과는 비어 있어야 정상이다.
     */
    public void testUsagesOfBuilderConstructorIncludeBuilderCallSites() {
        myFixture.addFileToProject("Sample.java", """
            import lombok.Builder;
            public class Sample {
                private final String name;
                @Builder(builderMethodName = "sampleBuilder")
                Sample(String name) { this.name = name; }
                public static SampleBuilder sampleBuilder() { return null; }
                public static class SampleBuilder {
                    public SampleBuilder name(String n) { return this; }
                    public Sample build() { return null; }
                }
            }
            """);
        myFixture.addFileToProject("Caller.java", """
            public class Caller {
                void use() { Sample.sampleBuilder().name("a").build(); }
            }
            """);

        PsiMethod constructor = findClass("Sample").getConstructors()[0];
        Collection<PsiReference> references = ReferencesSearch
            .search(constructor, GlobalSearchScope.projectScope(getProject()))
            .findAll();

        assertFalse("빌더 호출부가 생성자의 사용처로 보고돼야 한다", references.isEmpty());
        boolean fromCaller = references.stream().anyMatch(reference ->
            "Caller.java".equals(reference.getElement().getContainingFile().getName()));
        assertTrue("Caller.java 의 호출이 잡혀야 한다: " + describe(references), fromCaller);
    }

    /** 이름 변경이 빌더 호출부를 건드리면 안 된다 — 보고하는 참조는 표시용이다. */
    public void testReportedReferenceDoesNotRenameCallSites() {
        myFixture.addFileToProject("Sample.java", """
            import lombok.Builder;
            public class Sample {
                private final String name;
                @Builder(builderMethodName = "sampleBuilder")
                Sample(String name) { this.name = name; }
                public static SampleBuilder sampleBuilder() { return null; }
                public static class SampleBuilder {
                    public Sample build() { return null; }
                }
            }
            """);
        myFixture.addFileToProject("Caller.java", """
            public class Caller {
                void use() { Sample.sampleBuilder().build(); }
            }
            """);

        PsiMethod constructor = findClass("Sample").getConstructors()[0];
        for (PsiReference reference : ReferencesSearch
            .search(constructor, GlobalSearchScope.projectScope(getProject())).findAll()) {
            String before = reference.getElement().getText();
            reference.handleElementRename("Renamed");
            assertEquals("호출부 텍스트가 바뀌면 안 된다", before, reference.getElement().getText());
        }
    }

    // ---------- 도우미 ----------

    private PsiClass configureClass(@NotNull String source) {
        myFixture.configureByText("Sample.java", source);
        PsiClass[] classes = ((PsiJavaFile) myFixture.getFile()).getClasses();
        assertTrue("클래스가 없다", classes.length > 0);
        return classes[0];
    }

    private PsiClass findClass(@NotNull String name) {
        PsiClass found = JavaPsiFacade.getInstance(getProject())
            .findClass(name, GlobalSearchScope.projectScope(getProject()));
        assertNotNull(name + " 을 찾지 못했다", found);
        return found;
    }

    private static String describe(@NotNull Collection<PsiReference> references) {
        return references.stream()
            .map(reference -> reference.getElement().getContainingFile().getName()
                + ":" + reference.getElement().getText())
            .toList()
            .toString();
    }
}
