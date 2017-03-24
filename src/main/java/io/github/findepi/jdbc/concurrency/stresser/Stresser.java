package io.github.findepi.jdbc.concurrency.stresser;

import com.google.common.base.Strings;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.Exchanger;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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

        stress(connector, 2, 10);
        stress(connector, 2, 10_000);
        stress(connector, 10, 100);
        stress(connector, 10, 1_000_000);
        stress(connector, 25, 1_000_000);
    }

    private static void stress(Supplier<Connection> connector, int threads, long insertsPerThread)
            throws SQLException
    {
        System.out.printf("starting %s\n", threads);

        setup(connector);
        try {
            Exchanger<String> exchanger = new Exchanger<>();
            ExecutorService executor = Executors.newFixedThreadPool(threads);
            try {
                IntStream.range(0, threads)
                        .mapToObj(threadId -> executor.submit(() -> {
                            runThread(threadId, connector, exchanger, insertsPerThread);
                            return null;
                        }))
                        .collect(toList())
                        .forEach(future -> {
                            try {
                                future.get();
                            }
                            catch (InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                            catch (ExecutionException e) {
                                throw new RuntimeException(e);
                            }
                        });
            }
            finally {
                executor.shutdownNow();
            }
        }
        finally {
            tearDown(connector);
        }

        System.out.printf("Done OK %s threads, %s runs each\n", threads, insertsPerThread);
    }

    private static void runThread(int threadId, Supplier<Connection> connector, Exchanger<String> exchanger, long insertsPerThread)
            throws SQLException, InterruptedException
    {
        try (Connection connection = connector.get();
                PreparedStatement insert = connection.prepareStatement("insert into stress_test values (?,?)");
                PreparedStatement select = connection.prepareStatement("select pvalue from stress_test where pkey = ?");
        ) {
            for (long i = 0; i < insertsPerThread; i++) {
                insert.clearParameters();
                select.clearParameters();

                String key = "." + threadId + "." + i;

                insert.setString(1, key);
                insert.setString(2, "." + threadId + Strings.repeat("......" + i, 7));
                insert.execute();

                String otherKey = exchanger.exchange(key);
                select.setString(1, otherKey);
                try (ResultSet resultSet = select.executeQuery()) {
                    checkState(resultSet.next(), "no first row in rs");
                    checkState(resultSet.getString(1).contains("......"), "bogus");
                    checkState(!resultSet.next(), "second row in rs");
                }
            }
        }
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
