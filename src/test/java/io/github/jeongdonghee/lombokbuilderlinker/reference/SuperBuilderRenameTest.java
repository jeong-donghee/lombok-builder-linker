package io.github.jeongdonghee.lombokbuilderlinker.reference;

import com.intellij.psi.PsiLiteralExpression;
import io.github.jeongdonghee.lombokbuilderlinker.InplaceRenameTestCase;

/**
 * {@code @SuperBuilder} 의 이름 문자열에서 ⇧F6 이 호출부까지 다시 쓰는가 — 편집기 경로 그대로.
 *
 * <p>상속 체인이 걸린 자리가 핵심이다. 부모의 세터는 <b>부모 빌더</b>에 있고, 그 호출은
 * <b>자식 체인 안에도</b> 나온다. 부모의 접두사를 바꾸면 그 자리까지 따라와야 한다(2026-09-07 실측 확인).
 */
public class SuperBuilderRenameTest extends InplaceRenameTestCase {

    private static final String CALLER = """
        public class Caller {
            void use() {
                SuperParent p = SuperParent.parentBuilder().withParentField("p").create();
                SuperChild c = SuperChild.childBuilder().withParentField("p").setChildField("c").create();
            }
        }
        """;

    private static final String CHILD = """
        import lombok.experimental.SuperBuilder;
        @SuperBuilder(builderMethodName = "childBuilder", buildMethodName = "create", setterPrefix = "set")
        public class SuperChild extends SuperParent {
            private String childField;
        }
        """;

    /** 부모의 setterPrefix 를 바꾸면 두 체인에 흩어진 부모 세터 호출이 모두 따라와야 한다. */
    public void testRenamingParentSetterPrefix() {
        enableTemplates();
        myFixture.configureByText("SuperParent.java", """
            import lombok.experimental.SuperBuilder;
            @SuperBuilder(builderMethodName = "parentBuilder", buildMethodName = "create", setterPrefix = "wi<caret>th")
            public class SuperParent {
                private String parentField;
            }
            """);
        myFixture.addFileToProject("SuperChild.java", CHILD);
        myFixture.addFileToProject("Caller.java", CALLER);

        startInplace();
        myFixture.type("having");
        finishTemplate();

        assertTrue("애노테이션 문자열이 바뀌지 않았다:\n" + myFixture.getFile().getText(),
            myFixture.getFile().getText().contains("setterPrefix = \"having\""));
        String caller = fileText("Caller.java");
        assertFalse("옛 접두사가 남았다:\n" + caller, caller.contains(".withParentField("));
        assertEquals("부모 체인과 자식 체인 두 곳 모두 바뀌어야 한다:\n" + caller,
            2, countOf(caller, ".havingParentField("));
        assertTrue("자식 세터는 자식의 접두사이므로 그대로여야 한다:\n" + caller,
            caller.contains(".setChildField("));
    }

    /**
     * 자식의 진입 메서드 이름은 자기 것이므로 혼자 바꿔도 된다.
     *
     * <p>{@code buildMethodName} 은 여기서 다루지 않는다 — 부모와 자식이 같은 이름이어야 하므로
     * (자식 build 가 부모 것을 오버라이드한다) 한쪽만 바꾸면 컴파일이 깨진다. 그건 이 플러그인이
     * 아니라 {@code @SuperBuilder} 자체의 제약이다.
     */
    public void testRenamingChildBuilderMethodName() {
        enableTemplates();
        myFixture.addFileToProject("SuperParent.java", """
            import lombok.experimental.SuperBuilder;
            @SuperBuilder(builderMethodName = "parentBuilder", buildMethodName = "create", setterPrefix = "with")
            public class SuperParent {
                private String parentField;
            }
            """);
        myFixture.configureByText("SuperChild.java",
            CHILD.replace("\"childBuilder\"", "\"child<caret>Builder\""));
        myFixture.addFileToProject("Caller.java", CALLER);

        startInplace();
        myFixture.type("makeChild");
        finishTemplate();

        assertTrue("애노테이션 문자열이 바뀌지 않았다:\n" + myFixture.getFile().getText(),
            myFixture.getFile().getText().contains("builderMethodName = \"makeChild\""));
        String caller = fileText("Caller.java");
        assertTrue("자식 진입점 호출이 바뀌어야 한다:\n" + caller, caller.contains("SuperChild.makeChild()"));
        assertTrue("부모 진입점은 그대로여야 한다:\n" + caller, caller.contains("SuperParent.parentBuilder()"));
    }

    private void startInplace() {
        PsiLiteralExpression literal = BuilderNameRename.renamableLiteralAt(
            myFixture.getFile().findElementAt(myFixture.getCaretOffset()));
        assertNotNull("이름 변경 대상 문자열을 찾지 못했다", literal);
        String current = BuilderNameRename.currentName(literal);
        assertNotNull(current);
        assertTrue("in-place 편집을 시작하지 못했다",
            BuilderNameInplaceRename.startFromNameString(getProject(), myFixture.getEditor(), literal, current));
    }
}
