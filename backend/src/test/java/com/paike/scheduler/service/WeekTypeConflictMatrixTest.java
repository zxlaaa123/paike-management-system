package com.paike.scheduler.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V9 阶段 1B 红线：weekType 冲突矩阵 9 组合对拍基准。
 *
 * DB 版与 V3 贪心版两条冲突检测链路共用 {@link WeekTypeSupport#overlap}，
 * 因此本测试既验证矩阵语义正确，也作为两路对拍的"唯一真源"。
 * 验收要求：9/9 通过（V9_04 1B 验收红线）。
 */
class WeekTypeConflictMatrixTest {

    /** 矩阵 9 组合：a,b,预期是否冲突。顺序 ALL / ODD / EVEN × ALL / ODD / EVEN */
    @ParameterizedTest(name = "overlap({0}, {1}) = {2}")
    @CsvSource({
            // a,    b,    expected
            "ALL,   ALL,  true",
            "ALL,   ODD,  true",
            "ALL,   EVEN, true",
            "ODD,   ALL,  true",
            "ODD,   ODD,  true",
            "ODD,   EVEN, false",
            "EVEN,  ALL,  true",
            "EVEN,  ODD,  false",
            "EVEN,  EVEN, true"
    })
    void matrixOverlap(String a, String b, boolean expected) {
        assertEquals(expected, WeekTypeSupport.overlap(a, b),
                () -> "overlap(" + a + "," + b + ") 期望 " + expected);
    }

    /** 矩阵对称性：overlap(a,b) == overlap(b,a) */
    @Test
    void matrixIsSymmetric() {
        String[] types = {"ALL", "ODD", "EVEN"};
        for (String a : types) {
            for (String b : types) {
                assertEquals(WeekTypeSupport.overlap(a, b), WeekTypeSupport.overlap(b, a),
                        "矩阵必须对称: overlap(" + a + "," + b + ") != overlap(" + b + "," + a + ")");
            }
        }
    }

    /** null / 空 / 空白 视为 ALL（与 DB DEFAULT、存量回填语义一致） */
    @ParameterizedTest
    @CsvSource({
            ", ODD",
            "'', ODD",
            "' ', ODD",
            "ODD, ",
            "ODD, ''"
    })
    void nullOrBlankTreatedAsAll(String a, String b) {
        // null/空白 按 ALL，ALL 与 ODD 冲突
        assertTrue(WeekTypeSupport.overlap(a, b),
                "null/空白应视为 ALL 并与任意冲突");
    }

    /** 大小写不敏感、去空白 */
    @Test
    void normalizeHandlesCaseAndWhitespace() {
        assertEquals("ALL", WeekTypeSupport.normalize(null));
        assertEquals("ALL", WeekTypeSupport.normalize(""));
        assertEquals("ALL", WeekTypeSupport.normalize("  "));
        assertEquals("ODD", WeekTypeSupport.normalize("odd"));
        assertEquals("EVEN", WeekTypeSupport.normalize(" Even "));
    }

    /** 单双周同槽不冲突的核心场景：ODD 课与 EVEN 课可共存 */
    @Test
    void oddAndEvenCanCoexist() {
        assertFalse(WeekTypeSupport.overlap("ODD", "EVEN"));
        assertFalse(WeekTypeSupport.overlap("EVEN", "ODD"));
    }
}
