/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.streampark.console.base.config;

import org.apache.streampark.common.configuration.option.CoreOptions;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies the datasource.dialect validation and normalization in SpringProperties. */
class SpringPropertiesTest {

    private static final String APP_HOME_KEY = CoreOptions.APP_HOME.key();

    @TempDir
    Path appHome;

    private String originalAppHome;

    @BeforeEach
    void setUp() {
        originalAppHome = System.setProperty(APP_HOME_KEY, appHome.toAbsolutePath().toString());
    }

    @AfterEach
    void tearDown() {
        if (originalAppHome == null) {
            System.clearProperty(APP_HOME_KEY);
        } else {
            System.setProperty(APP_HOME_KEY, originalAppHome);
        }
    }

    private void writeConfig(String... datasourceEntries) throws IOException {
        Path confDir = Files.createDirectories(appHome.resolve("conf"));
        StringBuilder yaml = new StringBuilder("datasource:\n");
        for (String entry : datasourceEntries) {
            yaml.append("  ").append(entry).append('\n');
        }
        Files.write(confDir.resolve("config.yaml"), yaml.toString().getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void postgresqlDialectIsRejectedAsUnsupported() throws IOException {
        writeConfig("dialect: postgresql");

        UnsupportedOperationException error =
            assertThrows(UnsupportedOperationException.class, SpringProperties::get);

        assertTrue(error.getMessage().contains("postgresql"));
        assertTrue(error.getMessage().contains("supported values are: mysql, pgsql, h2"));
    }

    @Test
    void pgsqlDialectWithWhitespaceAndUppercaseIsAccepted() throws IOException {
        writeConfig("dialect: \"  PGSQL  \"");

        Properties properties = SpringProperties.get();

        assertEquals("pgsql", properties.getProperty("datasource.dialect"));
        assertEquals(
            "org.postgresql.Driver", properties.getProperty("spring.datasource.driver-class-name"));
    }

    @Test
    void unsupportedDialectListsSupportedValues() throws IOException {
        writeConfig("dialect: postgre");

        UnsupportedOperationException error =
            assertThrows(UnsupportedOperationException.class, SpringProperties::get);

        assertTrue(error.getMessage().contains("postgre"));
        assertTrue(error.getMessage().contains("supported values are: mysql, pgsql, h2"));
    }

    @Test
    void missingDialectFailsWithSupportedValuesHint() throws IOException {
        writeConfig("h2-data-dir: /tmp/streampark-h2");

        ExceptionInInitializerError error =
            assertThrows(ExceptionInInitializerError.class, SpringProperties::get);

        assertTrue(error.getMessage().contains("datasource.dialect is required"));
        assertTrue(error.getMessage().contains("supported values are: mysql, pgsql, h2"));
    }

    @Test
    void h2DialectSetsH2Driver() throws IOException {
        writeConfig("dialect: h2");

        Properties properties = SpringProperties.get();

        assertEquals("h2", properties.getProperty("datasource.dialect"));
        assertEquals("org.h2.Driver", properties.getProperty("spring.datasource.driver-class-name"));
    }
}
