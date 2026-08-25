package javax0.bubas.bunit;

import javax0.bubas.api.BubasException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

@DisplayName("the fixed BUNIT language")
class BunitLanguageTest {

    /**
     * Sealing is where the vocabulary is proved: overlap analysis rejects any two patterns that
     * could match one line, so this failing means two BUNIT statements are ambiguous.
     */
    @Test
    void the_vocabulary_seals() {
        assertThatCode(BunitLanguage::get).doesNotThrowAnyException();
    }

    @Test
    void a_whole_test_compiles() {
        assertThatCode(() -> BunitLanguage.get().compile("""
                PROGRAM ApproveOrderOverLimit
                    "LOAD_ORDER"  WITH 42   RETURNS "o1"
                    "ORDER_TOTAL" WITH "o1" RETURNS 1500.00
                    "APPROVE _" IS MOCKED

                    ARGUMENT "orderId" IS 42
                    ARGUMENT "limit"   IS 1000.00

                    RUN

                    RESULT IS FALSE
                    "APPROVE _" WAS NOT CALLED
                    "LOG_EVENT _, _" WAS CALLED WITH "INFO", "over limit: 1500.00"
                END.
                """)).doesNotThrowAnyException();
    }

    @Test
    void every_statement_form_compiles() {
        assertThatCode(() -> BunitLanguage.get().compile("""
                PROGRAM EveryForm
                    "F" RETURNS 1
                    "G" WITH 1 RETURNS 2
                    "H" WITH 1, 2 RETURNS 3
                    "C _" IS MOCKED
                    ARGUMENT "n" IS 1
                    RUN
                    RESULT IS 1
                    "F" WAS CALLED
                    "F" WAS NOT CALLED
                    "F" WAS CALLED WITH 1
                    "F" WAS CALLED WITH 1, 2
                    FAILED WITH "boom"
                END.
                """)).doesNotThrowAnyException();
    }

    @Test
    void a_test_may_compute_with_the_standard_statements() {
        assertThatCode(() -> BunitLanguage.get().compile("""
                PROGRAM Computed
                    DECLARE limit DECIMAL FINAL = 1000.00
                    ARGUMENT "limit" IS limit * 2
                    RUN
                    RESULT IS TRUE
                END.
                """)).doesNotThrowAnyException();
    }

    @Test
    void an_unknown_statement_is_still_rejected() {
        assertThat(catchThrowableOfType(BubasException.class,
                () -> BunitLanguage.get().compile("PROGRAM T\n    WIBBLE\nEND.\n")).getMessage())
                .contains("unknown statement WIBBLE");
    }
}
