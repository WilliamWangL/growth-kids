package com.growthkids.api.who.importer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.growthkids.engine.who.WhoIndicator;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** WHO CSV 解析器单测（TASK-008）。 */
class WhoCsvParserTest {

    private static final String VALID =
            """
            # source: test-source
            # version: 1.0
            sex,indicator,age_days,l_value,m_value,s_value
            male,height_for_age,0,1.0,49.9,0.04
            female,weight_for_age,30,0.5,4.2,0.11
            """;

    @Test
    void parsesValidFile() {
        WhoDataSet dataSet = parse(VALID);
        assertEquals("test-source", dataSet.source());
        assertEquals("1.0", dataSet.version());
        assertEquals(2, dataSet.size());
        ParsedLmsRow first = dataSet.rows().get(0);
        assertEquals("male", first.sex());
        assertEquals(WhoIndicator.HEIGHT_FOR_AGE, first.indicator());
        assertEquals(0, first.ageDays());
        assertEquals(new BigDecimal("1.0"), first.l());
        assertEquals(new BigDecimal("49.9"), first.m());
        assertEquals(new BigDecimal("0.04"), first.s());
    }

    @Test
    void handlesBomCommentsAndBlankLines() {
        String content =
                "\uFEFF# source: bom-source\n"
                        + "# version: 2.0\n"
                        + "# arbitrary comment\n"
                        + "\n"
                        + "sex,indicator,age_days,l_value,m_value,s_value\n"
                        + "\n"
                        + "male,bmi_for_age,100,-0.5,17.5,0.08\n";
        WhoDataSet dataSet = parse(content);
        assertEquals("bom-source", dataSet.source());
        assertEquals("2.0", dataSet.version());
        assertEquals(1, dataSet.size());
    }

    @Test
    void rejectsMissingSourceMetadata() {
        String content = VALID.replace("# source: test-source\n", "");
        WhoDataException e = assertThrows(WhoDataException.class, () -> parse(content));
        assertEquals(true, e.getMessage().contains("source"));
    }

    @Test
    void rejectsMissingVersionMetadata() {
        String content = VALID.replace("# version: 1.0\n", "");
        WhoDataException e = assertThrows(WhoDataException.class, () -> parse(content));
        assertEquals(true, e.getMessage().contains("version"));
    }

    @Test
    void rejectsWrongHeader() {
        String content = VALID.replace(
                "sex,indicator,age_days,l_value,m_value,s_value",
                "sex,indicator,age_days,L,M,S");
        WhoDataException e = assertThrows(WhoDataException.class, () -> parse(content));
        assertEquals(true, e.getMessage().contains("header"));
    }

    @Test
    void rejectsUnknownIndicator() {
        String content = VALID.replace("male,height_for_age,0", "male,shoe_size_for_age,0");
        WhoDataException e = assertThrows(WhoDataException.class, () -> parse(content));
        assertEquals(true, e.getMessage().contains("indicator"));
    }

    @Test
    void rejectsInvalidSex() {
        String content = VALID.replace("male,height_for_age,0", "boy,height_for_age,0");
        assertThrows(WhoDataException.class, () -> parse(content));
    }

    @Test
    void rejectsNegativeAgeDays() {
        String content = VALID.replace("male,height_for_age,0", "male,height_for_age,-1");
        printError(() -> parse(content));
    }

    @Test
    void rejectsNonNumericLms() {
        String content = VALID.replace("1.0,49.9,0.04", "1.0,abc,0.04");
        assertThrows(WhoDataException.class, () -> parse(content));
    }

    @Test
    void rejectsWrongColumnCount() {
        String content = VALID.replace("male,height_for_age,0,1.0,49.9,0.04", "male,height_for_age,0");
        WhoDataException e = assertThrows(WhoDataException.class, () -> parse(content));
        assertEquals(true, e.getMessage().contains("6 columns"));
    }

    private static void printError(Runnable action) {
        assertThrows(WhoDataException.class, action::run);
    }

    private static WhoDataSet parse(String content) {
        return new WhoCsvParser()
                .parse(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
    }
}
