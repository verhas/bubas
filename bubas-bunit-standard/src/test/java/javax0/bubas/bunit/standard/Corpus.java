package javax0.bubas.bunit.standard;

import javax0.bubas.analyser.BubasLanguage;
import javax0.bubas.api.Context;
import javax0.bubas.api.ExpressionArg;
import javax0.bubas.api.StatementContext;
import javax0.bubas.api.VariableArg;
import javax0.bubas.support.Standard;

import java.math.BigDecimal;

/**
 * The order-processing language the corpus subjects are written against.
 * <p>
 * Every implementation but {@code LOG} throws. A corpus test that reaches one has failed to mock
 * it, and saying so loudly is better than quietly returning a plausible value — half the corpus
 * exists to prove that mocks really do replace the implementations. {@code LOG} is real precisely
 * so that partial mocking has something to demonstrate.
 */
final class Corpus {

    private Corpus() {
    }

    public static final class Order {
    }

    public static final class Customer {
    }

    private static IllegalStateException unmocked() {
        return new IllegalStateException("a corpus subject must never reach a real implementation");
    }

    public static final class LoadOrder {
        public Order call(Context ctx, long id) {
            throw unmocked();
        }
    }

    public static final class OrderTotal {
        public BigDecimal call(Context ctx, Order order) {
            throw unmocked();
        }
    }

    public static final class CustomerOf {
        public Customer call(Context ctx, Order order) {
            throw unmocked();
        }
    }

    public static final class RiskOf {
        public String call(Context ctx, Customer customer) {
            throw unmocked();
        }
    }

    public static final class Approve {
        public void call(StatementContext ctx, ExpressionArg target) {
            throw unmocked();
        }
    }

    public static final class Reject {
        public void call(StatementContext ctx, ExpressionArg target, ExpressionArg reason) {
            throw unmocked();
        }
    }

    /** The one that is real, so a test can leave it unmocked and see it run. */
    public static final class Log {
        public void call(StatementContext ctx, ExpressionArg level, ExpressionArg message) {
            ctx.log(level.evaluate().asString(), message.evaluate().asString());
        }
    }

    /** Writes an INTEGER: a mock must supply it, because no token could stand in. */
    public static final class CountInto {
        public void call(StatementContext ctx, VariableArg total, ExpressionArg region) {
            throw unmocked();
        }
    }

    /** Writes an opaque value: the framework supplies a token, so a mock need not. */
    public static final class FetchInto {
        public void call(StatementContext ctx, VariableArg target, ExpressionArg id) {
            throw unmocked();
        }
    }

    static final BubasLanguage LANGUAGE = BubasLanguage.builder()
            .install(Standard::register)
            .defineOpaqueType("Order", Order.class)
            .defineOpaqueType("Customer", Customer.class)
            .defineFunction("LOAD_ORDER", LoadOrder.class)
            .defineFunction("ORDER_TOTAL", OrderTotal.class)
            .defineFunction("CUSTOMER_OF", CustomerOf.class)
            .defineFunction("RISK_OF", RiskOf.class)
            .defineStatement("APPROVE {expression/Order:target}", Approve.class)
            .defineStatement("REJECT {expression/Order:target}, {expression/STRING:reason}",
                    Reject.class)
            .defineStatement("LOG {expression/STRING:level}, {expression/STRING:message}", Log.class)
            .defineStatement("COUNT ORDERS INTO {new > identifier/INTEGER:total > initialized}"
                    + " FOR {expression/STRING:region}", CountInto.class)
            .defineStatement("FETCH {new > identifier/Order:target > initialized}"
                    + " BY {expression/INTEGER:id}", FetchInto.class)
            .seal();
}
