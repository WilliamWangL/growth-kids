package com.growthkids.api.who.importer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.growthkids.engine.who.WhoIndicator;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** WHO 数据校验器单测（TASK-008，规范 §35-7）：parser 格式校验之上的数据级检查。 */
class WhoDataValidatorTest {

    private final WhoDataValidator validator = new WhoDataValidator();

    @Test
    void acceptsValidDataSet() {
        WhoDataSet dataSet =
                dataSet(
                        row("male", WhoIndicator.HEIGHT_FOR_AGE, 0, "49.9"),
                        row("male", WhoIndicator.HEIGHT_FOR_AGE, 30, "54.2"),
                        row("female", WhoIndicator.WEIGHT_FOR_AGE, 0, "3.2"));
        assertDoesNotThrow(() -> validator.validate(dataSet));
    }

    @Test
    void rejectsEmptyDataSet() {
        WhoDataException e =
                assertThrows(WhoDataException.class, () -> validator.validate(dataSet()));
        assertTrue(e.getMessage().contains("no data rows"));
    }

    @Test
    void rejectsDuplicates() {
        WhoDataException e =
                assertThrows(
                        WhoDataException.class,
                        () ->
                                validator.validate(
                                        dataSet(
                                                row("male", WhoIndicator.HEIGHT_FOR_AGE, 0, "49.9"),
                                                row("male", WhoIndicator.HEIGHT_FOR_AGE, 0, "50.1"))));
        assertTrue(e.getMessage().contains("duplicate"));
    }

    @Test
    void rejectsNonIncreasingAges() {
        WhoDataException e =
                assertThrows(
                        WhoDataException.class,
                        () ->
                                validator.validate(
                                        dataSet(
                                                row("male", WhoIndicator.HEIGHT_FOR_AGE, 30, "54.2"),
                                                row("male", WhoIndicator.HEIGHT_FOR_AGE, 10, "51.0"))));
        assertTrue(e.getMessage().contains("not strictly increasing"));
    }

    @Test
    void rejectsNonPositiveM() {
        WhoDataException e =
                assertThrows(
                        WhoDataException.class,
                        () ->
                                validator.validate(
                                        dataSet(row("male", WhoIndicator.HEIGHT_FOR_AGE, 0, "0"))));
        assertTrue(e.getMessage().contains("m_value must be > 0"));
    }

    @Test
    void rejectsNonPositiveS() {
        WhoDataException e =
                assertThrows(
                        WhoDataException.class,
                        () ->
                                validator.validate(
                                        dataSet(
                                                rowWithS(
                                                        "male",
                                                        WhoIndicator.HEIGHT_FOR_AGE,
                                                        0,
                                                        "49.9",
                                                        "-0.01"))));
        assertTrue(e.getMessage().contains("s_value must be > 0"));
    }

    @Test
    void rejectsAgeBeyondLimit() {
        WhoDataException e =
                assertThrows(
                        WhoDataException.class,
                        () ->
                                validator.validate(
                                        dataSet(
                                                row(
                                                        "male",
                                                        WhoIndicator.HEIGHT_FOR_AGE,
                                                        7001,
                                                        "180.0"))));
        assertTrue(e.getMessage().contains("age_days exceeds 7000"));
    }

    @Test
    void reportsMultipleErrorsAtOnce() {
        WhoDataException e =
                assertThrows(
                        WhoDataException.class,
                        () ->
                                validator.validate(
                                        dataSet(
                                                row("male", WhoIndicator.HEIGHT_FOR_AGE, 0, "-1"),
                                                rowWithS(
                                                        "male",
                                                        WhoIndicator.WEIGHT_FOR_AGE,
                                                        0,
                                                        "3.3",
                                                        "0"))));
        assertTrue(e.getMessage().contains("2 error(s)"));
    }

    private static WhoDataSet dataSet(ParsedLmsRow... rows) {
        return new WhoDataSet("src", "1.0", List.of(rows));
    }

    private static ParsedLmsRow row(String sex, WhoIndicator indicator, int ageDays, String m) {
        return rowWithS(sex, indicator, ageDays, m, "0.04");
    }

    private static ParsedLmsRow rowWithS(
            String sex, WhoIndicator indicator, int ageDays, String m, String s) {
        return new ParsedLmsRow(
                sex,
                indicator,
                ageDays,
                new BigDecimal("1.0"),
                new BigDecimal(m),
                new BigDecimal(s));
    }
}

