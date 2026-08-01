package io.github.jeongdonghee.lombokbuilderlinker.reference;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.rename.RenameHandler;
import com.intellij.refactoring.rename.RenameHandlerRegistry;
import io.github.jeongdonghee.lombokbuilderlinker.LombokTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * ⇧F6 을 눌렀을 때 <b>이름 변경 처리기 선택 팝업이 뜨지 않아야 한다</b>.
 *
 * <p>왜 뜨던가: {@code RenameHandlerRegistry.doGetRenameHandlers} 는 손을 든
 * {@code RenameHandler} 를 표시 이름으로 키를 잡아 모으고, 둘 이상이면 선택 팝업을 띄운다
 * (특별 취급은 {@code MemberInplaceRenameHandler} 제거 하나뿐이고, 우선순위 수단은 없다).
 * 그리고 Lombok 플러그인의 {@code LombokElementRenameVetoHandler} 가 이렇게 손을 든다:
 *
 * <pre>
 * PsiElement e = PsiElementRenameHandler.getElement(dataContext);   // 캐럿의 참조 해석 결과
 * e instanceof LombokLightClassBuilder
 *   || ((e instanceof LombokLightMethodBuilder || e instanceof LombokLightFieldBuilder)
 *       &amp;&amp; e.getNavigationElement() instanceof PsiAnnotation)
 * </pre>
 *
 * <p>즉 <b>우리 참조가 Lombok 합성 멤버로 해석되는 것 자체가</b> 팝업의 원인이었다. 그래서 이름
 * 문자열이 합성 멤버를 가리키는 경우에는 참조 해석 결과를 내놓지 않는다 — 호출부를 찾는 일은
 * 해석에 기대지 않고 {@link LombokMemberReference#generatedTargets()} 로 따로 한다.
 *
 * <p>여기서는 캐럿 위치의 해석 결과가 Lombok 의 조건에 걸리지 않는지를 확인한다. Lombok 플러그인
 * 클래스는 컴파일 클래스패스에 없으므로 클래스 이름 문자열로 판정한다.
 */
public class RenameHandlerAmbiguityTest extends LombokTestCase {

    /** builderMethodName — 합성 static 메서드를 가리킨다. Lombok 이 손들지 않아야 한다. */
    public void testBuilderMethodNameDoesNotTriggerLombokVetoHandler() {
        myFixture.configureByText("ExactCase.java", """
            import lombok.Builder;
            public class ExactCase {
                private final String channelName;

                @Builder(builderMethodName = "history<caret>ChannelBuilder")
                public ExactCase(String channelName) { this.channelName = channelName; }
            }
            """);
        assertNoLombokVeto();
    }

    /** buildMethodName — 빌더 클래스 안의 합성 메서드. */
    public void testBuildMethodNameDoesNotTriggerLombokVetoHandler() {
        myFixture.configureByText("ExactCase.java", """
            import lombok.Builder;
            public class ExactCase {
                private final String channelName;

                @Builder(buildMethodName = "cre<caret>ate")
                public ExactCase(String channelName) { this.channelName = channelName; }
            }
            """);
        assertNoLombokVeto();
    }

    /** builderClassName — 합성 클래스. Lombok 은 이 경우 조건 없이 손을 든다. */
    public void testBuilderClassNameDoesNotTriggerLombokVetoHandler() {
        myFixture.configureByText("ExactCase.java", """
            import lombok.Builder;
            public class ExactCase {
                private final String channelName;

                @Builder(builderClassName = "Chan<caret>nelCreator")
                public ExactCase(String channelName) { this.channelName = channelName; }
            }
            """);
        assertNoLombokVeto();
    }

    /** setterPrefix — 이제 이름 변경 대상이므로, 여기서도 팝업이 뜨지 않아야 한다. */
    public void testSetterPrefixDoesNotTriggerLombokVetoHandler() {
        myFixture.configureByText("ExactCase.java", """
            import lombok.Builder;
            public class ExactCase {
                private final String channelName;

                @Builder(setterPrefix = "wi<caret>th")
                public ExactCase(String channelName) { this.channelName = channelName; }
            }
            """);
        assertNoLombokVeto();

        List<? extends RenameHandler> claiming =
            RenameHandlerRegistry.getInstance().getRenameHandlers(caretDataContext());
        assertEquals("손 든 처리기가 둘 이상이면 선택 팝업이 뜬다: " + names(claiming), 1, claiming.size());
    }

    /**
     * ObtainVia 는 반대다 — <b>직접 쓴</b> 메서드를 가리키므로 해석이 살아 있어야 한다.
     * 그게 살아 있어야 그 메서드의 Rename·Safe Delete 가 이 문자열을 보게 된다.
     */
    public void testObtainViaStillResolvesToTheRealMember() {
        myFixture.configureByText("ExactCase.java", """
            import lombok.Builder;
            @Builder(toBuilder = true)
            public class ExactCase {
                private String name;
                @Builder.ObtainVia(method = "comput<caret>eLength")
                private int length;
                public int computeLength() { return 0; }
            }
            """);
        PsiElement target = caretTarget();
        assertNotNull("ObtainVia 는 실제 멤버로 해석돼야 한다", target);
        assertFalse("직접 쓴 메서드이므로 Lombok 합성 멤버가 아니어야 한다", isLombokLight(target));
    }

    /**
     * 팝업 판정을 <b>플랫폼에 직접 물어본다</b> — 애노테이션 문자열에서는 손 든 처리기가 하나여야 한다.
     *
     * <p>{@code RenameHandlerRegistry.doGetRenameHandlers} 는 손 든 처리기를 표시 이름으로 모으고,
     * ({@code MemberInplaceRenameHandler} 를 뺀 뒤) 둘 이상 남으면 선택 팝업을 띄운다.
     * 그래서 여기서 세는 것은 "팝업이 뜨는가"와 같은 질문이다.
     *
     * <p><b>{@code getRenameHandler()} 로는 확인할 수 없다.</b> 그 메서드는 단위 테스트 모드에서
     * 팝업 대신 테스트용 선택자를 호출해 하나를 골라 준다 — 실제 IDE 에서 팝업이 뜨는데도
     * 테스트는 조용히 통과한다(실측으로 걸렀다). 반드시 목록을 봐야 한다.
     */
    public void testOnlyOneHandlerClaimsTheNameString() {
        myFixture.configureByText("ExactCase.java", """
            import lombok.Builder;
            public class ExactCase {
                private final String channelName;

                @Builder(builderMethodName = "history<caret>ChannelBuilder")
                public ExactCase(String channelName) { this.channelName = channelName; }
            }
            """);

        List<? extends RenameHandler> claiming =
            RenameHandlerRegistry.getInstance().getRenameHandlers(caretDataContext());

        assertEquals("손 든 처리기가 둘 이상이면 선택 팝업이 뜬다: " + names(claiming), 1, claiming.size());
        assertTrue("우리 처리기가 아니다: " + names(claiming),
            claiming.get(0) instanceof BuilderNameRenameHandler);
    }

    /**
     * <b>호출부에서는 ⇧F6 에 끼어들지 않는다.</b> 여기서 손을 들면 Lombok 의 veto 처리기와 둘이 되어
     * 선택 팝업이 뜬다 — 그 목록에는 Lombok 쪽 {@code toString()}(클래스명+해시)이 그대로 보인다.
     *
     * <p>이름 변경은 이름을 <b>정하는</b> 자리(애노테이션 문자열)에서만 한다. 이 테스트는 호출부에
     * 손을 뻗으려는 시도를 막는 울타리다.
     */
    public void testWeDoNotClaimRenameAtCallSites() {
        myFixture.addFileToProject("ExactCase.java", """
            import lombok.Builder;
            public class ExactCase {
                private final String channelName;

                @Builder(builderMethodName = "historyChannelBuilder")
                public ExactCase(String channelName) { this.channelName = channelName; }
            }
            """);
        myFixture.configureByText("Caller.java", """
            public class Caller {
                void use() { ExactCase.history<caret>ChannelBuilder().channelName("a").build(); }
            }
            """);

        List<? extends RenameHandler> claiming =
            RenameHandlerRegistry.getInstance().getRenameHandlers(caretDataContext());

        assertTrue("호출부에서 손을 들면 Lombok 처리기와 함께 선택 팝업이 뜬다: " + names(claiming),
            claiming.stream().noneMatch(handler -> handler instanceof BuilderNameRenameHandler));
    }

    // ---------- 도우미 ----------

    /** ⇧F6 이 보는 것과 같은 데이터 컨텍스트 — 캐럿 대상은 참조 검색 플래그로 뽑는다. */
    private DataContext caretDataContext() {
        return SimpleDataContext.builder()
            .add(CommonDataKeys.PROJECT, getProject())
            .add(CommonDataKeys.EDITOR, myFixture.getEditor())
            .add(CommonDataKeys.PSI_FILE, myFixture.getFile())
            .add(CommonDataKeys.PSI_ELEMENT, TargetElementUtil.findTargetElement(
                myFixture.getEditor(), TargetElementUtil.getInstance().getReferenceSearchFlags()))
            .build();
    }

    private static String names(@NotNull List<? extends RenameHandler> handlers) {
        return handlers.stream().map(handler -> handler.getClass().getName()).toList().toString();
    }

    private void assertNoLombokVeto() {
        PsiElement target = caretTarget();
        assertFalse(
            "Lombok 의 veto 처리기가 손을 들 조건에 걸렸다 → ⇧F6 에서 선택 팝업이 뜬다. 해석 결과: "
                + describe(target),
            triggersLombokVeto(target));
    }

    /** 편집기 캐럿 위치에서 플랫폼이 내놓는 대상 — 데이터 컨텍스트의 PSI_ELEMENT 가 이것이다. */
    private @Nullable PsiElement caretTarget() {
        return TargetElementUtil.findTargetElement(
            myFixture.getEditor(), TargetElementUtil.getInstance().getAllAccepted());
    }

    /** Lombok veto 처리기의 판정 조건을 그대로 재현한다. */
    private static boolean triggersLombokVeto(@Nullable PsiElement element) {
        if (element == null) {
            return false;
        }
        String type = element.getClass().getName();
        if (type.contains("LombokLightClassBuilder")) {
            return true;
        }
        boolean lightMember = type.contains("LombokLightMethodBuilder")
            || type.contains("LombokLightFieldBuilder");
        return lightMember && element.getNavigationElement() instanceof PsiAnnotation;
    }

    private static boolean isLombokLight(@NotNull PsiElement element) {
        return element.getClass().getName().contains("LombokLight");
    }

    private static String describe(@Nullable PsiElement element) {
        return element == null ? "null" : element.getClass().getName() + " / " + element;
    }
}
