package org.kore.raumschiffwerft.service.boundary.persistence;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Locale;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;

/**
 * Duennere Connection-Wrapper fuer die JDBC-Repositories: jede
 * Statement-Ausfuehrung (executeQuery, executeUpdate, execute, Batch- und
 * Large-Varianten) oeffnet einen eigenen Span `db.zugriff` mit dem SQL-Text
 * (`db.sql`), dem Operationsschluesselwort (`db.operation`) und dem System
 * (`db.system=postgresql`). Fehler werden mit Status ERROR und der
 * Ausnahme am Span markiert. Alle anderen Methoden werden 1:1 delegiert;
 * ohne laufenden Tracer (Unit-Tests ohne OpenTelemetry) entsteht ein
 * Noop-Span und der Wrapper ist transparent.
 */
final class GetracedVerbindung {

    private static final String ATTRIBUTE_SYSTEM = "db.system";

    private GetracedVerbindung() {
    }

    /** Tracer des Services fuer DB-Spans (Instrumentierung auf DB-Zugriffe). */
    static Tracer tracer(OpenTelemetry openTelemetry) {
        return openTelemetry.getTracer("durchlauferhitzer.db");
    }

    /** Wrappt die Verbindung; jede Statement-Ausfuehrung wird getract. */
    static Connection verbinden(Connection roh, Tracer tracer) {
        return (Connection) Proxy.newProxyInstance(
                GetracedVerbindung.class.getClassLoader(),
                new Class<?>[] { Connection.class },
                new VerbindungsHandler(roh, tracer));
    }

    private record VerbindungsHandler(Connection roh, Tracer tracer) implements InvocationHandler {

        @Override
        public Object invoke(Object proxy, Method methode, Object[] args) throws Throwable {
            if ("prepareStatement".equals(methode.getName())) {
                Object ergebnis = aufrufen(methode, args);
                return ergebnis == null ? null
                        : statementTracen((PreparedStatement) ergebnis, (String) args[0], tracer);
            }
            if ("prepareCall".equals(methode.getName())) {
                Object ergebnis = aufrufen(methode, args);
                return ergebnis == null ? null
                        : statementTracen((PreparedStatement) ergebnis, (String) args[0], tracer);
            }
            if ("equals".equals(methode.getName())) {
                return args != null && args.length == 1 && args[0] == proxy;
            }
            return aufrufen(methode, args);
        }

        private Object aufrufen(Method methode, Object[] args) throws Throwable {
            try {
                return methode.invoke(roh, args);
            } catch (InvocationTargetException e) {
                throw e.getTargetException();
            }
        }
    }

    /** Statement-Proxy: nur die execute*-Methoden werden als Span getract. */
    private static PreparedStatement statementTracen(PreparedStatement roh, String sql, Tracer tracer) {
        return (PreparedStatement) Proxy.newProxyInstance(
                GetracedVerbindung.class.getClassLoader(),
                new Class<?>[] { PreparedStatement.class },
                (proxy, methode, args) -> {
                    if ("equals".equals(methode.getName())) {
                        return args != null && args.length == 1 && args[0] == proxy;
                    }
                    if (!methode.getName().startsWith("execute")) {
                        try {
                            return methode.invoke(roh, args);
                        } catch (InvocationTargetException e) {
                            throw e.getTargetException();
                        }
                    }
                    Span span = tracer.spanBuilder("db.zugriff")
                            .setSpanKind(SpanKind.CLIENT)
                            .setAttribute("db.sql", sql)
                            .setAttribute("db.operation", operation(sql))
                            .setAttribute(ATTRIBUTE_SYSTEM, "postgresql")
                            .startSpan();
                    try (Scope ignoriert = span.makeCurrent()) {
                        Object ergebnis = methode.invoke(roh, args);
                        if (methode.getReturnType() == boolean.class
                                && ergebnis != null && !(Boolean) ergebnis) {
                            span.setStatus(StatusCode.ERROR, "keine Ergebnismenge");
                        }
                        return ergebnis;
                    } catch (InvocationTargetException e) {
                        span.recordException(e.getTargetException());
                        span.setStatus(StatusCode.ERROR);
                        throw e.getTargetException();
                    } finally {
                        span.end();
                    }
                });
    }

    private static String operation(String sql) {
        String[] teile = sql.trim().split("\\s+", 2);
        return teile.length > 0 ? teile[0].toLowerCase(Locale.ROOT) : "unbekannt";
    }
}
