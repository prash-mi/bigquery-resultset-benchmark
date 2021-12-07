package com.google.cloud.bigquery.benchmark;

import com.google.cloud.bigquery.*;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.IOException;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;
import com.simba.googlebigquery.jdbc.Driver;
import com.simba.googlebigquery.jdbc.DataSource;

@Fork(value = 1)
@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 1)
@Measurement(iterations = 2)
@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class BQBenchmark {
    private static BigQuery bigquery;
    private static String query =
            "SELECT date, county, state_name, confirmed_cases, deaths FROM bigquery_test_dataset.large_data_testing_table"
                    + " where date is not null and county is not null and state_name is not null order by date limit 300000";
    private static final long BQ_OPTIMAL_PAGE_SIZE = 100000;

    @Setup
    public void setUp() throws IOException {
        bigquery = BigQueryOptions.getDefaultInstance().getService();
    }

    @Benchmark
    public void benchmarkBQResultSetIteration(Blackhole blackhole) throws SQLException {
        ConnectionSettings connectionSettings =
                ConnectionSettings.newBuilder()
                        .setDefaultDataset(DatasetId.of("bigquery_test_dataset"))
                        .setNumBufferedRows(BQ_OPTIMAL_PAGE_SIZE) // page size
                        .build();
        Connection connection = bigquery.createConnection(connectionSettings);

        BigQueryResultSet bigQueryResultSet = connection.executeSelect(query);
        ResultSet rs = bigQueryResultSet.getResultSet();
        int cnt = 0;

        while (rs.next()) { // pagination starts after approx 120,000 records
            ++cnt;
        }
        blackhole.consume(cnt);
    }

    @Benchmark
    public void benchmarkBQQueryIteration(Blackhole blackhole) throws SQLException, InterruptedException {
        QueryJobConfiguration queryJobConfiguration = QueryJobConfiguration.newBuilder(query).build();
        TableResult results = bigquery.query(queryJobConfiguration); // no way to specify page size
        int cnt = 0;
        boolean hasPage = true;
        while (hasPage) {
            for (FieldValueList fvl : results.getValues()) {
                ++cnt;
            }
            if(results.hasNextPage()){
                results = results.getNextPage();//get next page
            }else {
                hasPage=false;
            }
        }
        blackhole.consume(cnt);
    }
    @Benchmark
    //TODO: Complete the logic
    public void benchmarkSimbaJdbc(Blackhole blackhole) throws SQLException {
        java.sql.Connection connection = null;
        DataSource ds = new
                com.simba.googlebigquery.jdbc.DataSource();
        ds.setURL(Constants.SIMBA_CONNECTION_URL);
        connection = ds.getConnection();
        System.out.println("Connection opened? "+!connection.isClosed());
        connection.close();


    }

    public static void main(String[] args) throws SQLException, RunnerException {
        Options opt = new OptionsBuilder()
                .include(BQBenchmark.class.getSimpleName())
                .forks(1)
                .build();
        new Runner(opt).run();
      //  new BQBenchmark().benchmarkSimbaJdbc(null);
    }
}
