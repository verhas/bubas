package javax0.bubas.doc.expense;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** Chapter 21's example is a whole embedding, so it is exercised as one. */
class FirstLanguageTest {

    @Test
    void the_smallest_complete_embedding_decides_a_claim() {
        assertThat(FirstLanguage.decide(new FirstLanguage.Claim("42.50"), new BigDecimal("200.00")))
                .isTrue();
        assertThat(FirstLanguage.decide(new FirstLanguage.Claim("900.00"), new BigDecimal("200.00")))
                .isFalse();
    }

    /** The same compiled program serves both decisions; nothing is recompiled between them. */
    @Test
    void one_program_serves_every_claim() {
        assertThat(FirstLanguage.PROGRAM.name()).isEqualTo("ApproveSmallClaim");
        assertThat(FirstLanguage.PROGRAM.language()).isSameAs(FirstLanguage.LANGUAGE);
    }
}
