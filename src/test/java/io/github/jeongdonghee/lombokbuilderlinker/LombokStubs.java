package io.github.jeongdonghee.lombokbuilderlinker;

import com.intellij.testFramework.fixtures.CodeInsightTestFixture;

/**
 * 테스트용 Lombok 애노테이션 스텁.
 *
 * <p>진짜 Lombok 을 테스트 클래스패스에 넣지 않는다. 이 플러그인이 보는 것은 애노테이션의
 * <b>정규화된 이름과 속성 이름</b>뿐이므로 스텁으로 충분하고, 의존성도 줄어든다.
 *
 * <p>다만 스텁에는 Lombok 의 애노테이션 프로세서가 없으므로 <b>빌더가 실제로 생성되지 않는다.</b>
 * 그래서 "생성된 멤버"를 가리키는 속성({@code builderMethodName} 등)의 테스트에서는 Lombok 이
 * 만들어낼 멤버를 픽스처에 <b>손으로 써 준다</b>. 플러그인 쪽 해석 로직은 표준 PSI 조회
 * ({@code findMethodsByName} / {@code findInnerClassByName})이므로, 그 멤버가 증강으로 생겼든
 * 손으로 썼든 같은 경로를 탄다.
 */
public final class LombokStubs {

    private LombokStubs() {}

    public static void add(CodeInsightTestFixture fixture) {
        fixture.addFileToProject("lombok/Builder.java", """
            package lombok;
            import java.lang.annotation.*;
            @Target({ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR})
            @Retention(RetentionPolicy.SOURCE)
            public @interface Builder {
                String builderMethodName() default "builder";
                String buildMethodName() default "build";
                String builderClassName() default "";
                boolean toBuilder() default false;
                String setterPrefix() default "";

                @Target({ElementType.FIELD, ElementType.PARAMETER})
                @Retention(RetentionPolicy.SOURCE)
                @interface ObtainVia {
                    String field() default "";
                    String method() default "";
                    boolean isStatic() default false;
                }

                @Target(ElementType.FIELD)
                @Retention(RetentionPolicy.SOURCE)
                @interface Default {}
            }
            """);
        fixture.addFileToProject("lombok/experimental/SuperBuilder.java", """
            package lombok.experimental;
            import java.lang.annotation.*;
            @Target({ElementType.TYPE, ElementType.CONSTRUCTOR})
            @Retention(RetentionPolicy.SOURCE)
            public @interface SuperBuilder {
                String builderMethodName() default "builder";
                String buildMethodName() default "build";
                boolean toBuilder() default false;
                String setterPrefix() default "";
            }
            """);
        fixture.addFileToProject("lombok/NoArgsConstructor.java",
            "package lombok; public @interface NoArgsConstructor {}");
    }
}
