package javax0.bubas.analyser.symbol;

import javax0.bubas.analyser.match.Vocabulary;
import javax0.bubas.api.BubasException;
import javax0.bubas.api.BubasType;
import javax0.bubas.lexer.Lexer;
import javax0.bubas.lexer.LogicalLine;
import javax0.bubas.lexer.Token;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class SymbolTableTest {

    private static final Vocabulary VOCABULARY = Vocabulary.builder()
            .function("LOAD_ORDER")
            .opaqueType("Order")
            .build();

    private static LogicalLine line(String source) {
        return Lexer.lex(source).stream().filter(LogicalLine::hasTokens).findFirst().orElseThrow();
    }

    /** A line whose only token is the name, which is all these tests need. */
    private static Token name(String text) {
        return line(text).tokens().getFirst();
    }

    private static SymbolTable table() {
        return new SymbolTable(VOCABULARY);
    }

    private static String rejection(Runnable action) {
        return catchThrowableOfType(BubasException.class, action::run).getMessage();
    }

    @Nested
    @DisplayName("declaring")
    class Declaring {

        @Test
        void a_declaration_is_remembered_with_its_type_and_mutability() {
            final var table = table();
            final var declared = table.declare(line("total"), name("total"), BubasType.DECIMAL, true);
            assertThat(declared.name()).isEqualTo("total");
            assertThat(declared.type()).isEqualTo(BubasType.DECIMAL);
            assertThat(declared.isFinal()).isTrue();
            assertThat(table.declared()).containsExactly(declared);
        }

        @Test
        void the_same_name_may_not_be_declared_twice() {
            final var table = table();
            table.declare(line("total"), name("total"), BubasType.DECIMAL, false);
            assertThat(rejection(() ->
                    table.declare(line("total"), name("total"), BubasType.INTEGER, false)))
                    .contains("'total' is already declared on line 1");
        }

        @Test
        void a_lookalike_name_collides_and_the_diagnostic_names_the_other_spelling() {
            final var table = table();
            table.declare(line("userId"), name("userId"), BubasType.INTEGER, false);
            assertThat(rejection(() ->
                    table.declare(line("UserID"), name("UserID"), BubasType.STRING, false)))
                    .contains("'UserID' collides with 'userId'")
                    .contains("names are unique ignoring case");
        }

        @Test
        void a_variable_may_not_be_named_after_its_type() {
            assertThat(rejection(() -> table()
                    .declare(line("order"), name("order"), BubasType.INTEGER, false)))
                    .contains("a variable may not be named after its type");
        }

        @Test
        void a_variable_may_not_take_a_reserved_name() {
            assertThat(rejection(() -> table()
                    .declare(line("LOAD_ORDER"), name("LOAD_ORDER"), BubasType.INTEGER, false)))
                    .contains("is reserved and cannot name a variable");
        }
    }

    @Nested
    @DisplayName("referring")
    class Referring {

        @Test
        void a_reference_resolves_to_the_declaration() {
            final var table = table();
            final var declared = table.declare(line("total"), name("total"), BubasType.DECIMAL, false);
            assertThat(table.reference(line("total"), name("total"))).isEqualTo(declared);
        }

        @Test
        void an_undeclared_name_is_rejected() {
            assertThat(rejection(() -> table().reference(line("ghost"), name("ghost"))))
                    .contains("'ghost' is not declared");
        }

        @Test
        void a_reference_must_be_spelled_as_declared() {
            final var table = table();
            table.declare(line("userId"), name("userId"), BubasType.INTEGER, false);
            assertThat(rejection(() -> table.reference(line("userid"), name("userid"))))
                    .isEqualTo("'userid' is declared as 'userId' (at 1:1)");
        }
    }

    @Nested
    @DisplayName("use tracking")
    class Reads {

        @Test
        void a_declared_variable_nobody_read_is_reported() {
            final var table = table();
            table.declare(line("used"), name("used"), BubasType.INTEGER, false);
            table.declare(line("spare"), name("spare"), BubasType.INTEGER, false);
            table.reference(line("used"), name("used"));
            assertThat(table.neverRead()).extracting(Variable::name).containsExactly("spare");
        }

        @Test
        void an_assignment_target_is_written_not_read() {
            // find() deliberately does not record a read: assigning to a variable is not using it.
            final var table = table();
            table.declare(line("total"), name("total"), BubasType.INTEGER, false);
            assertThat(table.find("total")).isPresent();
            assertThat(table.neverRead()).extracting(Variable::name).containsExactly("total");
        }

        @Test
        void find_is_case_insensitive_because_it_answers_existence_not_spelling() {
            final var table = table();
            table.declare(line("userId"), name("userId"), BubasType.INTEGER, false);
            assertThat(table.find("USERID")).isPresent();
        }

        @Test
        void nothing_declared_means_nothing_unread() {
            assertThatCode(() -> assertThat(table().neverRead()).isEmpty())
                    .doesNotThrowAnyException();
        }
    }
}
