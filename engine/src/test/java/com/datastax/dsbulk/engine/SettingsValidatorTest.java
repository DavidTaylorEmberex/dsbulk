/*
 * Copyright DataStax Inc.
 *
 * This software can be used solely with DataStax Enterprise. Please consult the license at
 * http://www.datastax.com/terms/datastax-dse-driver-license-terms
 */
package com.datastax.dsbulk.engine;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class SettingsValidatorTest {

  private PrintStream originalStderr;
  private PrintStream originalStdout;
  private ByteArrayOutputStream stderr;

  @SuppressWarnings("FieldCanBeLocal")
  private ByteArrayOutputStream stdout;

  private Logger root;
  private Level oldLevel;

  private static final String[] BAD_PARAMS_WRONG_TYPE = {
    "--connector.csv.recursive=tralse",
    "--log.stmt.maxQueryStringLength=NotANumber",
    "--log.stmt.maxBoundValueLength=NotANumber",
    "--log.stmt.maxBoundValues=NotANumber",
    "--log.stmt.maxInnerStatements=NotANumber",
    "--driver.port=notANumber",
    "--driver.pooling.local.connections=NotANumber",
    "--driver.pooling.remote.connections=notANumber",
    "--driver.pooling.local.requests=NotANumber",
    "--driver.pooling.remote.requests=notANumber",
    "--driver.query.fetchSize=NotANumber",
    "--driver.query.idempotence=notAboolean",
    "--driver.timestampGenerator=badInstance",
    "--driver.addressTranslator=badINstance",
    "--driver.policy.maxRetries=notANumber",
    "--connector.csv.skipLines=notANumber",
    "--connector.csv.maxLines=notANumber",
    "--connector.csv.recursive=tralse",
    "--connector.csv.header=tralse",
    "--connector.csv.encoding=1",
    "--connector.csv.delimiter=",
    "--connector.csv.quote=",
    "--connector.csv.escape=",
    "--connector.csv.comment=",
    "--connector.csv.maxConcurrentFiles=notANumber",
    "--schema.nullToUnset=tralse",
    "--batch.maxBatchSize=NotANumber",
    "--batch.bufferSize=NotANumber",
    "--executor.maxPerSecond=NotANumber",
    "--executor.maxInFlight=NotANumber",
    "--monitoring.expectedWrites=NotANumber",
    "--monitoring.expectedWrites=expectedReads",
    "--monitoring.jmx=tralse"
  };

  private static final String[] BAD_ENUM = {
    "--log.stmt.level=badValue",
    "--driver.protocol.compression=badValue",
    "--driver.query.consistency=badValue",
    "--driver.query.serialConsistency=badValue",
    "--batch.mode=badEnum",
    "--monitoring.rateUnit=badValue",
    "--monitoring.durationUnit=badValue",
  };

  private static final String[] BAD_DURATION = {
    "--driver.socket.readTimeout=badValue",
    "--driver.pooling.heartbeat=badValue",
    "--monitoring.reportRate=NotANumber",
  };

  @BeforeEach
  void setUp() throws Exception {
    originalStdout = System.out;
    originalStderr = System.err;
    stdout = new ByteArrayOutputStream();
    System.setOut(new PrintStream(stdout));
    stderr = new ByteArrayOutputStream();
    System.setErr(new PrintStream(stderr));
    root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    oldLevel = root.getLevel();
    root.setLevel(Level.INFO);
  }

  @AfterEach
  void tearDown() throws Exception {
    System.setOut(originalStdout);
    System.setErr(originalStderr);
    root.setLevel(oldLevel);
  }

  @Test
  void should_error_on_bad_arguments() {
    for (String argument : Arrays.asList(BAD_PARAMS_WRONG_TYPE)) {
      int starting_index = argument.indexOf("-", argument.indexOf("-argument") + 1) + 2;
      int ending_index = argument.indexOf("=");
      String argPrefix = argument.substring(starting_index, ending_index);
      new Main(
              new String[] {
                "load",
                "--schema.keyspace=keyspace",
                "--schema.table=table",
                "--connector.csv.url=127.0.1.1",
                argument
              })
          .run();
      String err = new String(stderr.toByteArray(), StandardCharsets.UTF_8);
      assertThat(err).contains(argPrefix + " has type");
      assertThat(err).contains(" rather than");
    }

    // These errors will look like this; String: 1: Invalid value at 'protocol.compression':
    // The enum class Compression has no constant of the name 'badValue' (should be one of [NONE,
    // SNAPPY, LZ4].
    for (String argument : Arrays.asList(BAD_ENUM)) {
      new Main(
              new String[] {
                "load",
                "--schema.keyspace=keyspace",
                "--schema.table=table",
                "--connector.csv.url=127.0.1.1",
                argument
              })
          .run();
      String err = new String(stderr.toByteArray(), StandardCharsets.UTF_8);
      assertThat(err).contains("Invalid value at");
      assertThat(err).contains("The enum class");
    }

    // These errors will look like this; String: 1: Invalid value at 'socket.readTimeout': No number
    // in duration value 'badValue'
    for (String argument : Arrays.asList(BAD_DURATION)) {
      new Main(
              new String[] {
                "load",
                "--schema.keyspace=keyspace",
                "--schema.table=table",
                "--connector.csv.url=127.0.1.1",
                argument
              })
          .run();
      String err = new String(stderr.toByteArray(), StandardCharsets.UTF_8);
      assertThat(err).contains("Invalid value at");
      assertThat(err).contains(" No number in duration value");
    }
  }

  @Test
  void should_error_bad_connector() throws Exception {
    new Main(new String[] {"load", "-c", "BadConnector"}).run();
    String err = new String(stderr.toByteArray(), StandardCharsets.UTF_8);
    assertThat(err).contains("Cannot find connector");
  }

  @Test
  void should_error_on_empty_hosts() throws Exception {
    new Main(new String[] {"load", "--driver.hosts", ""}).run();
    String err = new String(stderr.toByteArray(), StandardCharsets.UTF_8);
    assertThat(err)
        .contains(
            "driver.hosts is mandatory. Please set driver.hosts and try again. "
                + "See settings.md or help for more information");
  }

  @Test
  void should_error_on_empty_url() throws Exception {
    new Main(new String[] {"load", "--driver.hosts=hostshere", "--connector.csv.url", ""}).run();
    String err = new String(stderr.toByteArray(), StandardCharsets.UTF_8);
    assertThat(err)
        .contains(
            "url is mandatory when using the csv connector. Please set connector.csv.url and "
                + "try again. See settings.md or help for more information.");
  }

  @Test
  void should_error_bad_parse_driver_option() throws Exception {
    new Main(new String[] {"load", "--driver.socket.readTimeout", "I am not a duration"}).run();
    String err = new String(stderr.toByteArray(), StandardCharsets.UTF_8);
    assertThat(err).contains("Invalid value at 'socket.readTimeout'");
  }

  @Test
  void should_error_invalid_auth_provider() throws Exception {
    new Main(new String[] {"load", "--driver.auth.provider", "InvalidAuthProvider"}).run();
    String err = new String(stderr.toByteArray(), StandardCharsets.UTF_8);
    assertThat(err).contains("InvalidAuthProvider is not a valid auth provider");
  }

  @Test
  void should_error_invalid_auth_combinations_missing_principal() throws Exception {
    new Main(
            new String[] {
              "load", "--driver.auth.provider=DseGSSAPIAuthProvider", "--driver.auth.principal", ""
            })
        .run();
    String err = new String(stderr.toByteArray(), StandardCharsets.UTF_8);
    assertThat(err).contains("must be provided with auth.principal");
  }

  @Test
  void should_error_invalid_auth_combinations_missing_username() throws Exception {
    new Main(
            new String[] {
              "load", "--driver.auth.provider=PlainTextAuthProvider", "--driver.auth.username", ""
            })
        .run();
    String err = new String(stderr.toByteArray(), StandardCharsets.UTF_8);
    assertThat(err).contains("must be provided with both auth.username and auth.password");
  }

  @Test
  void should_error_invalid_auth_combinations_missing_password() throws Exception {
    new Main(
            new String[] {
              "load",
              "--driver.auth.provider=DsePlainTextAuthProvider",
              "--driver.auth.password",
              ""
            })
        .run();
    String err = new String(stderr.toByteArray(), StandardCharsets.UTF_8);
    assertThat(err).contains("must be provided with both auth.username and auth.password");
  }

  @Test
  void should_error_invalid_schema_settings() throws Exception {
    new Main(new String[] {"load", "--connector.csv.url=/path/to/my/file"}).run();
    String err = new String(stderr.toByteArray(), StandardCharsets.UTF_8);
    assertThat(err).contains("schema.mapping, or schema.keyspace and schema.table must be defined");
  }

  @Test
  void should_error_invalid_schema_query_with_ttl() throws Exception {
    new Main(
            new String[] {
              "load",
              "--connector.csv.url=/path/to/my/file",
              "--schema.query=xyz",
              "--schema.queryTtl=30",
              "--schema.keyspace=keyspace",
              "--schema.table=table"
            })
        .run();
    String err = new String(stderr.toByteArray(), StandardCharsets.UTF_8);
    assertThat(err)
        .contains(
            "schema.query must not be defined if schema.queryTtl or schema.queryTimestamp "
                + "is defined");
  }

  @Test
  void should_error_invalid_schema_query_with_timestamp() throws Exception {
    new Main(
            new String[] {
              "load",
              "--connector.csv.url=/path/to/my/file",
              "--schema.query=xyz",
              "--schema.queryTimestamp=9876123",
              "--schema.keyspace=keyspace",
              "--schema.table=table"
            })
        .run();
    String err = new String(stderr.toByteArray(), StandardCharsets.UTF_8);
    assertThat(err)
        .contains(
            "schema.query must not be defined if schema.queryTtl or schema.queryTimestamp "
                + "is defined");
  }

  @Test
  void should_error_invalid_schema_query_with_mapped_timestamp() throws Exception {
    new Main(
            new String[] {
              "load",
              "--connector.csv.url=/path/to/my/file",
              "--schema.query=xyz",
              "--schema.mapping",
              "f1=__timestamp",
              "--schema.keyspace=keyspace",
              "--schema.table=table"
            })
        .run();
    String err = new String(stderr.toByteArray(), StandardCharsets.UTF_8);
    assertThat(err)
        .contains("schema.query must not be defined when mapping a field to query-timestamp");
  }

  @Test
  void should_error_invalid_schema_query_with_mapped_ttl() throws Exception {
    new Main(
            new String[] {
              "load",
              "--connector.csv.url=/path/to/my/file",
              "--schema.query=xyz",
              "--schema.mapping",
              "f1=__ttl",
              "--schema.keyspace=keyspace",
              "--schema.table=table"
            })
        .run();
    String err = new String(stderr.toByteArray(), StandardCharsets.UTF_8);
    assertThat(err).contains("schema.query must not be defined when mapping a field to query-ttl");
  }

  @Test
  void should_error_invalid_timestamp() throws Exception {
    new Main(
            new String[] {
              "load",
              "--connector.csv.url=/path/to/my/file",
              "--schema.queryTimestamp=junk",
              "--schema.keyspace=keyspace",
              "--schema.table=table"
            })
        .run();
    String err = new String(stderr.toByteArray(), StandardCharsets.UTF_8);
    assertThat(err).contains("Could not parse schema.queryTimestamp 'junk'");
  }

  @Test
  void should_connect_error_with_schema_defined() throws Exception {
    new Main(
            new String[] {
              "load",
              "--driver.hosts=255.255.255.23",
              "--connector.csv.url=/path/to/my/file",
              "--schema.keyspace=keyspace",
              "--schema.table=table"
            })
        .run();
    String err = new String(stderr.toByteArray(), StandardCharsets.UTF_8);
    assertThat(err).contains(" All host(s) tried for query");
  }

  @Test
  void should_error_invalid_schema_missing_keyspace() throws Exception {
    new Main(new String[] {"load", "--connector.csv.url=/path/to/my/file", "--schema.table=table"})
        .run();
    String err = new String(stderr.toByteArray(), StandardCharsets.UTF_8);
    assertThat(err).contains("schema.keyspace must accompany schema.table in the configuration");
  }

  @Test
  void should_error_invalid_schema_mapping_missing_keyspace_and_table() throws Exception {
    new Main(new String[] {"load", "--connector.csv.url=/path/to/my/file", "-m", "c1=c2"}).run();
    String err = new String(stderr.toByteArray(), StandardCharsets.UTF_8);
    assertThat(err).contains("schema.query, or schema.keyspace and schema.table must be defined");
  }

  @Test
  void should_connect_error_with_schema_mapping_query_defined() throws Exception {
    new Main(
            new String[] {
              "load",
              "--driver.hosts=255.255.255.23",
              "--connector.csv.url=/path/to/my/file",
              "-m",
              "c1=c2",
              "--schema.query",
              "INSERT INTO KEYSPACE (f1, f2) VALUES (:f1, :f2)"
            })
        .run();
    String err = new String(stderr.toByteArray(), StandardCharsets.UTF_8);
    assertThat(err).contains(" All host(s) tried for query");
  }

  @Test
  void should_error_invalid_schema_inferred_mapping_query_defined() throws Exception {
    new Main(
            new String[] {
              "load",
              "--connector.csv.url=/path/to/my/file",
              "-m",
              "*=*, c1=c2",
              "--schema.query",
              "INSERT INTO KEYSPACE (f1, f2) VALUES (:f1, :f2)"
            })
        .run();
    String err = new String(stderr.toByteArray(), StandardCharsets.UTF_8);
    assertThat(err)
        .contains("schema.keyspace and schema.table must be defined when using inferred mapping");
  }

  @Test
  void should_error_unknown_lbp() throws Exception {
    new Main(
            new String[] {
              "load",
              "--connector.csv.url=/path/to/my/file",
              "-m",
              "c1=c2",
              "--driver.policy.lbp.name",
              "unknown",
              "--schema.query",
              "INSERT INTO KEYSPACE (f1, f2) VALUES (:f1, :f2)"
            })
        .run();
    String err = new String(stderr.toByteArray(), StandardCharsets.UTF_8);
    assertThat(err).contains("Invalid value at 'policy.lbp.name'");
  }

  @Test
  void should_error_lbp_bad_child_policy() throws Exception {
    new Main(
            new String[] {
              "load",
              "--connector.csv.url=/path/to/my/file",
              "-m",
              "c1=c2",
              "--driver.policy.lbp.name",
              "dse",
              "--driver.policy.lbp.dse.childPolicy",
              "junk",
              "--schema.query",
              "INSERT INTO KEYSPACE (f1, f2) VALUES (:f1, :f2)"
            })
        .run();
    String err = new String(stderr.toByteArray(), StandardCharsets.UTF_8);
    assertThat(err).contains("Invalid value at 'dse.childPolicy'");
  }

  @Test
  void should_error_lbp_chaining_loop_self() throws Exception {
    new Main(
            new String[] {
              "load",
              "--connector.csv.url=/path/to/my/file",
              "-m",
              "c1=c2",
              "--driver.policy.lbp.name",
              "dse",
              "--driver.policy.lbp.dse.childPolicy",
              "dse",
              "--schema.query",
              "INSERT INTO KEYSPACE (f1, f2) VALUES (:f1, :f2)"
            })
        .run();

    String err = new String(stderr.toByteArray(), StandardCharsets.UTF_8);
    assertThat(err).contains("Load balancing policy chaining loop detected: dse,dse");
  }

  @Test
  void should_error_lbp_chaining_loop() throws Exception {
    new Main(
            new String[] {
              "load",
              "--connector.csv.url=/path/to/my/file",
              "-m",
              "c1=c2",
              "--driver.policy.lbp.name",
              "dse",
              "--driver.policy.lbp.dse.childPolicy",
              "whiteList",
              "--driver.policy.lbp.whiteList.childPolicy",
              "tokenAware",
              "--driver.policy.lbp.tokenAware.childPolicy",
              "whiteList",
              "--schema.query",
              "INSERT INTO KEYSPACE (f1, f2) VALUES (:f1, :f2)"
            })
        .run();
    String err = new String(stderr.toByteArray(), StandardCharsets.UTF_8);
    assertThat(err)
        .contains(
            "Load balancing policy chaining loop detected: dse,whiteList,tokenAware,whiteList");
  }
}
