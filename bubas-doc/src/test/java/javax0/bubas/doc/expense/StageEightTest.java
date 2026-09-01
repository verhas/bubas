package javax0.bubas.doc.expense;

import javax0.bubas.export.VocabularyExport;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stage 8: a type from the standard library.
 * <p>
 * {@code java.time.LocalDate} cannot carry an annotation, so it is described through an interface
 * standing in for it — the one case {@code defineOpaqueTypeVia} exists for. Every other type in
 * this vocabulary is described on its own class. See {@code DOCUMENTATION/BOOK/defining-types.md}.
 */
class StageEightTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 3, 20);
    private static final BigDecimal LIMIT = new BigDecimal("200.00");

    private static Expense.Report claim(long id, String who, LocalDate filed, String amount) {
        return Expense.claim(id, who, filed,
                Expense.item("travel", "City Taxi", amount, true));
    }

    @Test
    void a_claim_filed_too_late_is_refused() throws IOException {
        final var program = Expense.STAGE_8.compile(Runs.source("timely-expense.bu"));

        final var outcomes = List.of(
                Runs.run(program, Runs.args("claim", claim(1, "Alice", TODAY.minusDays(4), "42.50"),
                        "today", TODAY, "limit", LIMIT)),
                Runs.run(program, Runs.args("claim", claim(2, "Bob", TODAY.minusDays(45), "42.50"),
                        "today", TODAY, "limit", LIMIT)));

        assertThat(outcomes.get(0).answer()).isTrue();
        assertThat(outcomes.get(1).logged()).singleElement().asString()
                .contains("filed 45 days ago").contains("window is 30");

        Runs.write("stage8-timely.txt", Runs.transcript(outcomes));
    }

    /** The borrowed type exports its description exactly as a self-described one does. */
    @Test
    void a_type_described_through_an_interface_exports_like_any_other() {
        assertThat(VocabularyExport.of(Expense.STAGE_8).types())
                .filteredOn(type -> type.name().equals("Date"))
                .singleElement()
                .satisfies(type -> assertThat(type.description()).contains("A calendar day"));
    }
}
