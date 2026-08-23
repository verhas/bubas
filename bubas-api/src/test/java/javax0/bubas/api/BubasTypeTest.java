package javax0.bubas.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class BubasTypeTest {

    interface Document {
    }

    static class Order implements Document {
    }

    static class RushOrder extends Order {
    }

    static class Customer {
    }

    private static final BubasType ORDER = BubasType.opaque("Order", Order.class);
    private static final BubasType RUSH = BubasType.opaque("RushOrder", RushOrder.class);
    private static final BubasType DOCUMENT = BubasType.opaque("Document", Document.class);
    private static final BubasType CUSTOMER = BubasType.opaque("Customer", Customer.class);

    @Nested
    @DisplayName("java types")
    class JavaTypes {

        @Test
        void scalars_map_to_their_natural_java_types() {
            assertThat(BubasType.INTEGER.javaType()).isEqualTo(long.class);
            assertThat(BubasType.DECIMAL.javaType()).isEqualTo(BigDecimal.class);
            assertThat(BubasType.STRING.javaType()).isEqualTo(String.class);
            assertThat(BubasType.BOOLEAN.javaType()).isEqualTo(boolean.class);
            assertThat(BubasType.VOID.javaType()).isEqualTo(void.class);
        }

        @Test
        void an_opaque_type_maps_to_its_registered_class() {
            assertThat(ORDER.javaType()).isEqualTo(Order.class);
        }

        @Test
        void arrays_map_to_native_java_arrays() {
            assertThat(BubasType.arrayOf(BubasType.INTEGER).javaType()).isEqualTo(long[].class);
            assertThat(BubasType.arrayOf(BubasType.DECIMAL).javaType()).isEqualTo(BigDecimal[].class);
            assertThat(BubasType.arrayOf(BubasType.BOOLEAN).javaType()).isEqualTo(boolean[].class);
            assertThat(BubasType.arrayOf(ORDER).javaType()).isEqualTo(Order[].class);
        }

        @Test
        void the_element_agnostic_array_maps_to_BubasArray() {
            assertThat(BubasType.ANY_ARRAY.javaType()).isEqualTo(BubasArray.class);
        }
    }

    @Nested
    @DisplayName("assignability")
    class Assignability {

        @Test
        void a_type_accepts_itself() {
            assertThat(BubasType.STRING.accepts(BubasType.STRING)).isTrue();
            assertThat(ORDER.accepts(BubasType.opaque("Order", Order.class))).isTrue();
        }

        @Test
        void integer_widens_to_decimal_but_not_back() {
            assertThat(BubasType.DECIMAL.accepts(BubasType.INTEGER)).isTrue();
            assertThat(BubasType.INTEGER.accepts(BubasType.DECIMAL)).isFalse();
        }

        @Test
        void unrelated_scalars_never_mix() {
            assertThat(BubasType.STRING.accepts(BubasType.INTEGER)).isFalse();
            assertThat(BubasType.BOOLEAN.accepts(BubasType.INTEGER)).isFalse();
            assertThat(BubasType.INTEGER.accepts(BubasType.STRING)).isFalse();
        }

        @Test
        void opaque_widening_follows_java_including_interfaces() {
            assertThat(ORDER.accepts(RUSH)).isTrue();
            assertThat(DOCUMENT.accepts(ORDER)).isTrue();
            assertThat(RUSH.accepts(ORDER)).isFalse();
            assertThat(ORDER.accepts(CUSTOMER)).isFalse();
        }

        @Test
        void an_opaque_type_never_accepts_a_scalar() {
            assertThat(ORDER.accepts(BubasType.STRING)).isFalse();
            assertThat(BubasType.STRING.accepts(ORDER)).isFalse();
        }

        @Test
        void arrays_are_invariant() {
            // A handler holding the backing store could otherwise write a RushOrder slot with a
            // plain Order, which the script's declared element type forbids.
            assertThat(BubasType.arrayOf(ORDER).accepts(BubasType.arrayOf(RUSH))).isFalse();
            assertThat(BubasType.arrayOf(ORDER).accepts(BubasType.arrayOf(ORDER))).isTrue();
        }

        @Test
        void an_integer_array_is_not_a_decimal_array() {
            assertThat(BubasType.arrayOf(BubasType.DECIMAL)
                    .accepts(BubasType.arrayOf(BubasType.INTEGER))).isFalse();
        }

        @Test
        void any_array_accepts_every_array_and_nothing_else() {
            assertThat(BubasType.ANY_ARRAY.accepts(BubasType.arrayOf(BubasType.INTEGER))).isTrue();
            assertThat(BubasType.ANY_ARRAY.accepts(BubasType.arrayOf(ORDER))).isTrue();
            assertThat(BubasType.ANY_ARRAY.accepts(BubasType.STRING)).isFalse();
            assertThat(BubasType.ANY_ARRAY.accepts(BubasType.ANY_ARRAY)).isFalse();
        }

        @Test
        void void_accepts_nothing() {
            assertThat(BubasType.VOID.accepts(BubasType.VOID)).isTrue();
            assertThat(BubasType.VOID.accepts(BubasType.INTEGER)).isFalse();
            assertThat(BubasType.INTEGER.accepts(BubasType.VOID)).isFalse();
        }
    }

    @Nested
    @DisplayName("diagnostics")
    class Naming {

        @Test
        void a_type_reads_as_the_script_would_write_it() {
            assertThat(ORDER).hasToString("Order");
            assertThat(BubasType.INTEGER).hasToString("INTEGER");
            assertThat(BubasType.arrayOf(ORDER)).hasToString("Order[]");
            assertThat(BubasType.arrayOf(BubasType.INTEGER)).hasToString("INTEGER[]");
        }
    }
}
