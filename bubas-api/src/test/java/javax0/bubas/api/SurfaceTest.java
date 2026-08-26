package javax0.bubas.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("the public surface of a class, and its checksum")
class SurfaceTest {

    public static class Order {
        public String reference;
        public static final int LIMIT = 10;

        public BigDecimal total() {
            return BigDecimal.ZERO;
        }

        public boolean isExpedited() {
            return false;
        }

        private String hidden() {
            return "not public, not on the surface";
        }

        protected String alsoHidden() {
            return "not public either";
        }
    }

    /** Same surface, different implementations: what a caller sees is what is hashed. */
    public static class OrderRewritten {
        public String reference;
        public static final int LIMIT = 10;

        public BigDecimal total() {
            return BigDecimal.ONE.add(BigDecimal.TEN);
        }

        public boolean isExpedited() {
            return true;
        }

        private String different() {
            return "a new private helper";
        }
    }

    public static class OrderWithMore extends Order {
        public BigDecimal surcharge() {
            return BigDecimal.ZERO;
        }
    }

    @Test
    void the_surface_is_the_public_members() {
        assertThat(Surface.of(Order.class))
                .contains("java.math.BigDecimal total()", "boolean isExpedited()",
                        "java.lang.String reference", "static int LIMIT");
    }

    @Test
    void what_is_not_public_is_not_on_it() {
        assertThat(Surface.of(Order.class))
                .noneMatch(member -> member.contains("hidden"));
    }

    /** Object is on everything, so it says nothing about this class. */
    @Test
    void the_members_of_Object_are_excluded() {
        assertThat(Surface.of(Order.class))
                .noneMatch(member -> member.contains("hashCode") || member.contains("wait"));
    }

    @Test
    void it_is_sorted_so_the_checksum_does_not_depend_on_reflection_order() {
        assertThat(Surface.of(Order.class)).isSorted();
    }

    @Test
    void an_inherited_public_member_counts_as_much_as_a_declared_one() {
        assertThat(Surface.of(OrderWithMore.class))
                .contains("java.math.BigDecimal total()", "java.math.BigDecimal surcharge()");
    }

    @Test
    void a_checksum_is_short_enough_to_type() {
        assertThat(Surface.checksum(Order.class)).hasSize(16).matches("[0-9A-F]+");
    }

    @Test
    void the_same_surface_gives_the_same_checksum() {
        assertThat(Surface.checksum(Order.class))
                .isEqualTo(Surface.checksum(Order.class));
    }

    /**
     * The point of hashing the surface rather than the code: rewriting a body does not invalidate
     * a description, and this is also the limit — a behaviour change moves nothing.
     */
    @Test
    void changing_bodies_and_private_members_does_not_move_the_checksum() {
        assertThat(Surface.of(OrderRewritten.class)).isEqualTo(Surface.of(Order.class));
        assertThat(Surface.checksum(OrderRewritten.class))
                .isEqualTo(Surface.checksum(Order.class));
    }

    @Test
    void gaining_a_member_moves_the_checksum() {
        assertThat(Surface.checksum(OrderWithMore.class))
                .isNotEqualTo(Surface.checksum(Order.class));
    }
}
