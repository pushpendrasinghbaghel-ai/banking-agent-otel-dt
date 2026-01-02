package com.banking.agent.config;

import com.zaxxer.hikari.HikariDataSource;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.*;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * DataSource configuration that wraps connections and statements
 * to capture actual SQL queries executed against the database.
 * 
 * This provides visibility into:
 * - Actual SQL statements (db.statement)
 * - Query execution time
 * - Row counts for updates
 * - Errors at the JDBC level
 */
@Configuration
public class TracingDataSourceConfig {

    private static final Logger log = LoggerFactory.getLogger(TracingDataSourceConfig.class);

    private final Tracer tracer;

    public TracingDataSourceConfig(Tracer tracer) {
        this.tracer = tracer;
        log.info("TracingDataSourceConfig initialized - SQL statements will be traced");
    }

    @Bean
    @Primary
    public DataSource dataSource(DataSourceProperties properties) {
        HikariDataSource hikariDataSource = properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
        
        return new TracingDataSource(hikariDataSource, tracer);
    }

    /**
     * DataSource wrapper that creates traced connections.
     */
    static class TracingDataSource implements DataSource {
        private final DataSource delegate;
        private final Tracer tracer;

        TracingDataSource(DataSource delegate, Tracer tracer) {
            this.delegate = delegate;
            this.tracer = tracer;
        }

        @Override
        public Connection getConnection() throws SQLException {
            return wrapConnection(delegate.getConnection());
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return wrapConnection(delegate.getConnection(username, password));
        }

        private Connection wrapConnection(Connection connection) {
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[] { Connection.class },
                    new TracingConnectionHandler(connection, tracer)
            );
        }

        @Override
        public PrintWriter getLogWriter() throws SQLException {
            return delegate.getLogWriter();
        }

        @Override
        public void setLogWriter(PrintWriter out) throws SQLException {
            delegate.setLogWriter(out);
        }

        @Override
        public void setLoginTimeout(int seconds) throws SQLException {
            delegate.setLoginTimeout(seconds);
        }

        @Override
        public int getLoginTimeout() throws SQLException {
            return delegate.getLoginTimeout();
        }

        @Override
        public java.util.logging.Logger getParentLogger() throws SQLFeatureNotSupportedException {
            return delegate.getParentLogger();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            return delegate.unwrap(iface);
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) throws SQLException {
            return delegate.isWrapperFor(iface);
        }
    }

    /**
     * Connection proxy handler that wraps statements.
     */
    static class TracingConnectionHandler implements InvocationHandler {
        private final Connection connection;
        private final Tracer tracer;

        TracingConnectionHandler(Connection connection, Tracer tracer) {
            this.connection = connection;
            this.tracer = tracer;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String methodName = method.getName();
            
            try {
                Object result = method.invoke(connection, args);
                
                // Wrap statement creation methods
                if (result instanceof PreparedStatement && args != null && args.length > 0) {
                    String sql = args[0].toString();
                    return wrapPreparedStatement((PreparedStatement) result, sql);
                } else if (result instanceof Statement && !(result instanceof PreparedStatement)) {
                    return wrapStatement((Statement) result);
                }
                
                return result;
            } catch (Exception e) {
                if (e.getCause() != null) {
                    throw e.getCause();
                }
                throw e;
            }
        }

        private Statement wrapStatement(Statement statement) {
            return (Statement) Proxy.newProxyInstance(
                    Statement.class.getClassLoader(),
                    new Class<?>[] { Statement.class },
                    new TracingStatementHandler(statement, null, tracer)
            );
        }

        private PreparedStatement wrapPreparedStatement(PreparedStatement statement, String sql) {
            return (PreparedStatement) Proxy.newProxyInstance(
                    PreparedStatement.class.getClassLoader(),
                    new Class<?>[] { PreparedStatement.class },
                    new TracingStatementHandler(statement, sql, tracer)
            );
        }
    }

    /**
     * Statement proxy handler that traces query execution.
     */
    static class TracingStatementHandler implements InvocationHandler {
        private final Statement statement;
        private final String preparedSql;
        private final Tracer tracer;

        TracingStatementHandler(Statement statement, String preparedSql, Tracer tracer) {
            this.statement = statement;
            this.preparedSql = preparedSql;
            this.tracer = tracer;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String methodName = method.getName();
            
            // Only trace execution methods
            if (isExecutionMethod(methodName)) {
                String sql = extractSql(methodName, args);
                return traceExecution(method, args, sql);
            }
            
            try {
                return method.invoke(statement, args);
            } catch (Exception e) {
                if (e.getCause() != null) {
                    throw e.getCause();
                }
                throw e;
            }
        }

        private boolean isExecutionMethod(String methodName) {
            return methodName.equals("execute") ||
                   methodName.equals("executeQuery") ||
                   methodName.equals("executeUpdate") ||
                   methodName.equals("executeBatch") ||
                   methodName.equals("executeLargeBatch") ||
                   methodName.equals("executeLargeUpdate");
        }

        private String extractSql(String methodName, Object[] args) {
            // For PreparedStatement, use the prepared SQL
            if (preparedSql != null) {
                return preparedSql;
            }
            // For Statement.execute(sql), the SQL is the first argument
            if (args != null && args.length > 0 && args[0] instanceof String) {
                return (String) args[0];
            }
            return "UNKNOWN";
        }

        private Object traceExecution(Method method, Object[] args, String sql) throws Throwable {
            String operation = extractOperation(sql);
            String spanName = "JDBC " + operation;

            Span span = tracer.spanBuilder(spanName)
                    .setSpanKind(SpanKind.CLIENT)
                    .setAttribute("db.system", "h2")
                    .setAttribute("db.name", "bankingdb")
                    .setAttribute("db.operation", operation)
                    .setAttribute("db.statement", truncateSql(sql))
                    .startSpan();

            try (Scope scope = span.makeCurrent()) {
                Object result = method.invoke(statement, args);
                
                // Capture affected rows for updates
                if (result instanceof Integer) {
                    span.setAttribute("db.rows_affected", (Integer) result);
                } else if (result instanceof Long) {
                    span.setAttribute("db.rows_affected", (Long) result);
                }
                
                span.setStatus(StatusCode.OK);
                return result;
                
            } catch (Exception e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                span.setStatus(StatusCode.ERROR, cause.getMessage());
                span.recordException(cause);
                throw cause;
            } finally {
                span.end();
            }
        }

        private String extractOperation(String sql) {
            if (sql == null || sql.isEmpty()) {
                return "UNKNOWN";
            }
            String normalized = sql.trim().toUpperCase();
            if (normalized.startsWith("SELECT")) return "SELECT";
            if (normalized.startsWith("INSERT")) return "INSERT";
            if (normalized.startsWith("UPDATE")) return "UPDATE";
            if (normalized.startsWith("DELETE")) return "DELETE";
            if (normalized.startsWith("CREATE")) return "CREATE";
            if (normalized.startsWith("DROP")) return "DROP";
            if (normalized.startsWith("ALTER")) return "ALTER";
            return "OTHER";
        }

        private String truncateSql(String sql) {
            // Truncate very long SQL statements to avoid span attribute limits
            if (sql != null && sql.length() > 4096) {
                return sql.substring(0, 4093) + "...";
            }
            return sql;
        }
    }
}
