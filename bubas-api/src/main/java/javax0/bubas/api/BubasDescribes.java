package javax0.bubas.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an interface as the place another class's BUBAS documentation lives.
 * <p>
 * An opaque type is usually a domain class — {@code Order}, {@code Claim} — that a REST layer, a
 * rules engine and BUBAS all use. Annotating it would make the domain model depend on one of its
 * consumers, and describing it at each registration would put the same prose in as many places as
 * there are languages exposing it. An empty interface carrying the annotations is neither: the
 * domain class stays ignorant of BUBAS, and the description has exactly one home.
 *
 * <pre>
 * &#64;BubasDescribes(Order.class)
 * &#64;BubasDescription("A customer's order, as the order service knows it.")
 * public interface OrderDoc {
 * }
 * </pre>
 *
 * Registered with {@code defineOpaqueTypeVia("Order", OrderDoc.class)}. It is a separate call
 * rather than something {@code defineOpaqueType} notices, because registering a class other than
 * the one named in the call is the kind of helpfulness that reads well until it surprises someone.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface BubasDescribes {
    /** The class actually being described, and the one that gets registered. */
    Class<?> value();
}
