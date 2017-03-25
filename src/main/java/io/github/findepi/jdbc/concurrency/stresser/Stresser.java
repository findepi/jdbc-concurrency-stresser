package io.github.findepi.jdbc.concurrency.stresser;

import com.google.common.base.Strings;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static com.google.common.base.Preconditions.checkState;
import static java.util.stream.Collectors.toList;

/**
 * @author findepi
 * @since 2017-03-24
 */
public class Stresser
{
    public static void main(String[] args)
            throws Exception
    {
        // TODO parameter validation, usage
        String jdbcUrl = args[0];
        String user = args[1];
        String password = args[2];

        System.out.println("Stresser starting up");

        Supplier<Connection> connector = createConnector(jdbcUrl, user, password);

        try {
            tearDown(connector);
        }
        catch (SQLException ignored) {
        }

        stress(connector, 1, 1, 10);
        stress(connector, 2, 30, 10_000);
        stress(connector, 10, 10, 100);
        stress(connector, 10, 10, 1_000_000);
        stress(connector, 25, 25, 1_000_000);
    }

    private static void stress(Supplier<Connection> connector, int writeThreads, int readThreads, long insertsPerThread)
            throws SQLException, InterruptedException
    {
        System.out.printf("starting %s writers, %s readers\n", writeThreads, readThreads);

        AtomicReference<String> lastWritten = new AtomicReference<>(null);
        AtomicBoolean done = new AtomicBoolean(false);

        setup(connector);
        try {
            ExecutorService executor = Executors.newFixedThreadPool(writeThreads + readThreads);
            try {

                IntStream.range(0, readThreads)
                        .mapToObj(_i -> reader(connector, lastWritten, done))
                        .forEach(executor::submit);

                List<Callable<Void>> writers = IntStream.range(0, writeThreads)
                        .mapToObj(threadId -> writer(threadId, connector, lastWritten, insertsPerThread))
                        .collect(toList());

                executor.invokeAll(writers);
            }
            finally {
                done.set(true);
                executor.shutdownNow();
            }
        }
        finally {
            tearDown(connector);
        }

        System.out.printf("Done OK %s writers, %s readers, %s runs each\n", writeThreads, readThreads, insertsPerThread);
    }

    private static Callable<Void> writer(int threadId, Supplier<Connection> connector, AtomicReference<String> lastWritten, long insertsPerThread)
    {
        return () -> {
            try (Connection connection = connector.get();
                    PreparedStatement insert = connection.prepareStatement("insert into stress_test values (?,?)")) {
                connection.setAutoCommit(false);

                for (long i = 0; i < insertsPerThread; i++) {
                    insert.clearParameters();

                    String key = "." + threadId + "." + i;

                    insert.setString(1, key);
                    insert.setString(2, "." + threadId + Strings.repeat("......" + i, 7));
                    insert.execute();
                    connection.commit();

                    lastWritten.set(key);
                }
            }

            return null;
        };
    }

    private static Callable<?> reader(Supplier<Connection> connector, AtomicReference<String> lastWritten, AtomicBoolean done)
    {
        return () -> {
            try (Connection connection = connector.get();
                    PreparedStatement select = connection.prepareStatement("select pvalue from stress_test where pkey = ?")) {
                connection.setAutoCommit(false);

                while (!done.get()) {
                    String otherKey = lastWritten.get();
                    if (otherKey == null) {
                        // nothing written yet
                        continue;
                    }

                    select.setString(1, otherKey);
                    try (ResultSet resultSet = select.executeQuery()) {
                        checkState(resultSet.next(), "no first row in rs");
                        checkState(resultSet.getString(1).contains("......"), "bogus");
                        checkState(!resultSet.next(), "second row in rs");
                    }
                    connection.commit();
                }
            }

            return null;
        };
    }

    private static void setup(Supplier<Connection> connector)
            throws SQLException
    {
        executeQuery(connector, "create table stress_test(pkey varchar(20) primary key, pvalue varchar(256))");
    }

    private static void tearDown(Supplier<Connection> connector)
            throws SQLException
    {
        executeQuery(connector, "drop table stress_test");
    }

    private static void executeQuery(Supplier<Connection> connector, String queryWithoutParameters)
            throws SQLException
    {
        try (Connection connection = connector.get()) {
            try (Statement statement = connection.createStatement()) {
                statement.execute(queryWithoutParameters);
            }
        }
    }

    private static Supplier<Connection> createConnector(String url, String user, String password)
    {
        return () -> {
            try {
                return DriverManager.getConnection(url, user, password);
            }
            catch (SQLException e) {
                throw new RuntimeException(e);
            }
        };
    }
}
