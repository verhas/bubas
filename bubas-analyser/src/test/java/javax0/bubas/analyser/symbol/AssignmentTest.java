package javax0.bubas.analyser.symbol;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AssignmentTest {

    private static final Assignment START = Assignment.start();

    @Nested
    @DisplayName("along one path")
    class Straight {

        @Test
        void nothing_is_initialized_to_begin_with() {
            assertThat(START.isInitialized("x")).isFalse();
            assertThat(START.reachable()).isTrue();
        }

        @Test
        void initializing_is_remembered() {
            assertThat(START.initialize("x").isInitialized("x")).isTrue();
        }

        @Test
        void an_unreachable_point_cannot_initialize_anything() {
            assertThat(Assignment.unreachable().initialize("x").isInitialized("x")).isFalse();
        }

        @Test
        void the_value_is_never_mutated_in_place() {
            final var before = START.initialize("x");
            before.initialize("y");
            assertThat(before.isInitialized("y")).isFalse();
        }
    }

    @Nested
    @DisplayName("where paths rejoin")
    class Merging {

        @Test
        void only_what_every_path_assigned_survives() {
            final var left = START.initialize("x").initialize("y");
            final var right = START.initialize("y");
            final var joined = left.merge(right);
            assertThat(joined.isInitialized("y")).isTrue();
            assertThat(joined.isInitialized("x")).isFalse();
        }

        @Test
        void an_IF_with_no_ELSE_merges_the_branch_with_the_path_that_skipped_it() {
            // The entry state is the else-path: whatever the branch did is not guaranteed.
            assertThat(START.initialize("x").merge(START).isInitialized("x")).isFalse();
        }

        @Test
        void a_branch_that_returned_contributes_nothing_rather_than_ruling_everything_out() {
            // IF bad THEN RETURN FALSE END IF  — after it, the live path's knowledge stands.
            final var afterReturn = Assignment.unreachable();
            final var live = START.initialize("x");
            assertThat(afterReturn.merge(live).isInitialized("x")).isTrue();
            assertThat(live.merge(afterReturn).isInitialized("x")).isTrue();
        }

        @Test
        void a_join_of_only_dead_paths_is_itself_dead() {
            final var joined = Assignment.unreachable().merge(Assignment.unreachable());
            assertThat(joined.reachable()).isFalse();
        }

        @Test
        void a_join_of_live_paths_is_live() {
            assertThat(START.merge(START).reachable()).isTrue();
        }

        @Test
        void an_ELSEIF_chain_merges_every_arm_at_once() {
            final var arms = List.of(
                    START.initialize("grade"),
                    START.initialize("grade"),
                    START.initialize("grade"));
            assertThat(Assignment.merge(arms).isInitialized("grade")).isTrue();
        }

        @Test
        void one_arm_forgetting_to_assign_loses_it_for_the_whole_chain() {
            final var arms = List.of(
                    START.initialize("grade"),
                    START,
                    START.initialize("grade"));
            assertThat(Assignment.merge(arms).isInitialized("grade")).isFalse();
        }
    }

    @Nested
    @DisplayName("the loop rules follow from merging")
    class Loops {

        @Test
        void a_pre_test_loop_guarantees_nothing_because_the_body_may_not_run() {
            final var entry = START;
            final var bodyExit = entry.initialize("count");
            assertThat(entry.merge(bodyExit).isInitialized("count")).isFalse();
        }

        @Test
        void a_post_test_loop_keeps_what_the_body_assigned_because_it_always_runs() {
            final var bodyExit = START.initialize("count");
            assertThat(bodyExit.isInitialized("count")).isTrue();
        }

        @Test
        void a_FOR_variable_is_assigned_on_entry_so_it_survives_either_way() {
            final var entry = START.initialize("i");
            final var bodyExit = entry.initialize("sum");
            final var after = entry.merge(bodyExit);
            assertThat(after.isInitialized("i")).isTrue();
            assertThat(after.isInitialized("sum")).isFalse();
        }
    }
}
