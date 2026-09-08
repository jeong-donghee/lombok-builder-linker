package io.github.jeongdonghee.lombokbuilderlinker.inspection;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInspection.LocalInspectionEP;
import io.github.jeongdonghee.lombokbuilderlinker.LombokTestCase;

import java.util.List;

/**
 * {@code @Builder.ObtainVia} 의 모양 검사.
 *
 * <p>기준은 Lombok 이 {@code toBuilder()} 본문에 <b>실제로 심는 코드</b>다({@code HandleBuilder}):
 * 기본은 {@code this.method()}, {@code isStatic = true} 면 {@code Type.method(this)}.
 * 어긋나면 생성된 코드가 컴파일되지 않는데, 그 코드는 소스에 없어 편집기는 조용하다.
 */
public class ObtainViaSignatureInspectionTest extends LombokTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        myFixture.enableInspections(new ObtainViaSignatureInspection());
    }

    /**
     * plugin.xml 에 실제로 등록돼 있는가.
     *
     * <p>나머지 테스트는 인스펙션 객체를 직접 만들어 쓰므로, 등록을 빠뜨려도 전부 통과한다 —
     * 그러면 IDE 에서는 아무 경고도 안 뜬다. 그 착각을 막는다.
     */
    public void testInspectionIsRegistered() {
        boolean registered = LocalInspectionEP.LOCAL_INSPECTION.getExtensionList().stream()
            .anyMatch(ep -> "ObtainViaSignature".equals(ep.getShortName()));
        assertTrue("plugin.xml 의 localInspection 등록이 빠졌다", registered);
    }

    /** 무인자 인스턴스 메서드 — Lombok 이 부르는 모양 그대로다. */
    public void testNoArgMethodIsFine() {
        assertNoWarning("""
            import lombok.Builder;
            @Builder(toBuilder = true)
            public class Sample {
                private String name;
                @Builder.ObtainVia(method = "computeLength")
                private int length;
                public int computeLength() { return 0; }
            }
            """);
    }

    /** 인자를 받으면 {@code this.method()} 호출이 컴파일되지 않는다. */
    public void testMethodWithArgumentsIsReported() {
        assertWarning("""
            import lombok.Builder;
            @Builder(toBuilder = true)
            public class Sample {
                private String name;
                @Builder.ObtainVia(method = "computeLength")
                private int length;
                public int computeLength(int seed) { return seed; }
            }
            """, "must take none");
    }

    /** {@code isStatic = true} 는 인스턴스를 인자로 받는 static 메서드를 부른다. */
    public void testStaticMethodTakingTheInstanceIsFine() {
        assertNoWarning("""
            import lombok.Builder;
            @Builder(toBuilder = true)
            public class Sample {
                private String name;
                @Builder.ObtainVia(method = "lengthOf", isStatic = true)
                private int length;
                public static int lengthOf(Sample sample) { return 0; }
            }
            """);
    }

    /** static 이 아니면 {@code Type.method(this)} 가 컴파일되지 않는다. */
    public void testInstanceMethodUnderIsStaticIsReported() {
        assertWarning("""
            import lombok.Builder;
            @Builder(toBuilder = true)
            public class Sample {
                private String name;
                @Builder.ObtainVia(method = "lengthOf", isStatic = true)
                private int length;
                public int lengthOf() { return 0; }
            }
            """, "must be static and take exactly one argument");
    }

    /** 이름이 아무것도 가리키지 않는 경우 — rename·safe delete 뒤에 남는 전형적인 상태다. */
    public void testUnknownMethodIsReported() {
        assertWarning("""
            import lombok.Builder;
            @Builder(toBuilder = true)
            public class Sample {
                private String name;
                @Builder.ObtainVia(method = "gone")
                private int length;
            }
            """, "cannot find a method named 'gone'");
    }

    /** {@code field} 쪽도 같다. */
    public void testUnknownFieldIsReported() {
        assertWarning("""
            import lombok.Builder;
            @Builder(toBuilder = true)
            public class Sample {
                private String name;
                @Builder.ObtainVia(field = "gone")
                private String alias;
            }
            """, "cannot find a field named 'gone'");
    }

    /** 상속받은 메서드도 Lombok 이 부를 수 있다 — 거짓 경고를 내면 안 된다. */
    public void testInheritedMethodIsFine() {
        myFixture.addFileToProject("Base.java", """
            public class Base {
                public int computeLength() { return 0; }
            }
            """);
        assertNoWarning("""
            import lombok.Builder;
            @Builder(toBuilder = true)
            public class Sample extends Base {
                private String name;
                @Builder.ObtainVia(method = "computeLength")
                private int length;
            }
            """);
    }

    // ---------- 도우미 ----------

    private void assertWarning(String source, String expectedFragment) {
        List<String> messages = warnings(source);
        assertTrue("경고가 나와야 한다(포함: " + expectedFragment + "): " + messages,
            messages.stream().anyMatch(message -> message.contains(expectedFragment)));
    }

    private void assertNoWarning(String source) {
        List<String> messages = warnings(source);
        assertTrue("경고가 없어야 한다: " + messages, messages.isEmpty());
    }

    /** 우리 인스펙션이 낸 것만 고른다 — Lombok 플러그인이 내는 다른 표시와 섞이지 않게. */
    private List<String> warnings(String source) {
        myFixture.configureByText("Sample.java", source);
        return myFixture.doHighlighting().stream()
            .map(HighlightInfo::getDescription)
            .filter(description -> description != null
                && (description.contains("Lombok calls") || description.contains("Lombok cannot find")))
            .toList();
    }
}
