package io.github.jeongdonghee.lombokbuilderlinker.reference;

import com.intellij.psi.PsiLiteralExpression;
import io.github.jeongdonghee.lombokbuilderlinker.InplaceRenameTestCase;

/**
 * {@code @Singular("item")} 의 이름을 ⇧F6 으로 바꾸면 호출부까지 함께 바뀌는가.
 *
 * <p>접두사가 걸린 자리가 핵심이다. 문자열은 {@code item} 인데 호출부에 적히는 이름은
 * {@code withItem} 이라, 새 이름을 그대로 쓰면 접두사가 사라진다. Lombok 의 접근자 이름 규칙대로
 * 다시 조립해야 한다({@code with} + {@code task} &rarr; {@code withTask}).
 */
public class SingularNameRenameTest extends InplaceRenameTestCase {

    /** 접두사 없음 — 문자열과 호출부 이름이 같다. */
    public void testRenamingSingularNameUpdatesCallSites() {
        enableTemplates();
        myFixture.configureByText("Plain.java", """
            import java.util.List;
            import lombok.Builder;
            import lombok.Singular;
            @Builder
            public class Plain {
                @Singular("it<caret>em")
                private List<String> items;
            }
            """);
        myFixture.addFileToProject("Caller.java", """
            public class Caller {
                void use() { Plain.builder().item("a").item("b").build(); }
            }
            """);

        startInplace();
        myFixture.type("task");
        finishTemplate();

        assertTrue("애노테이션 문자열이 바뀌지 않았다:\n" + myFixture.getFile().getText(),
            myFixture.getFile().getText().contains("@Singular(\"task\")"));
        String caller = fileText("Caller.java");
        assertFalse("옛 이름이 호출부에 남았다:\n" + caller, caller.contains(".item("));
        assertEquals("두 호출부 모두 새 이름이어야 한다:\n" + caller, 2, countOf(caller, ".task("));
    }

    /** 접두사 있음 — 새 이름에 접두사를 다시 붙이고 첫 글자를 올려야 한다. */
    public void testRenamingSingularNameKeepsTheSetterPrefix() {
        enableTemplates();
        myFixture.configureByText("Prefixed.java", """
            import java.util.List;
            import lombok.Builder;
            import lombok.Singular;
            @Builder(setterPrefix = "with")
            public class Prefixed {
                @Singular("it<caret>em")
                private List<String> items;
            }
            """);
        myFixture.addFileToProject("Caller.java", """
            public class Caller {
                void use() { Prefixed.builder().withItem("a").build(); }
            }
            """);

        startInplace();
        myFixture.type("task");
        finishTemplate();

        assertTrue("애노테이션 문자열이 바뀌지 않았다:\n" + myFixture.getFile().getText(),
            myFixture.getFile().getText().contains("@Singular(\"task\")"));
        String caller = fileText("Caller.java");
        assertTrue("접두사를 유지한 채 바뀌어야 한다(withItem -> withTask):\n" + caller,
            caller.contains(".withTask(\"a\")"));
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
