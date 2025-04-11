package org.jenkinsci.plugins.workflowhttp.cps;

import static org.junit.jupiter.api.Assertions.assertEquals;

import hudson.EnvVars;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CachingConfigurationTest {

    static EnvVars getEnvVars(String[] keys, String[] values) {
        EnvVars envVars = new EnvVars();
        for (int i = 0; i < keys.length; i++) {
            envVars.put(keys[i], values[i]);
        }
        return envVars;
    }

    static Stream<Arguments> isExcludedTestCases() {
        return Stream.of(
                Arguments.of("", new EnvVars(), false),
                Arguments.of("VAR=test", getEnvVars(new String[] {"VAR"}, new String[] {"test"}), true),
                Arguments.of("VAR=t*", getEnvVars(new String[] {"VAR"}, new String[] {"test"}), true),
                Arguments.of("VAR=*t", getEnvVars(new String[] {"VAR"}, new String[] {"test"}), true),
                Arguments.of("VAR=*n*", getEnvVars(new String[] {"VAR"}, new String[] {"test"}), false),
                Arguments.of("VAR=test VAR=*n* ", getEnvVars(new String[] {"VAR"}, new String[] {"test"}), true),
                Arguments.of(" VAR=*n*   VAR=test ", getEnvVars(new String[] {"VAR"}, new String[] {"test"}), true),
                Arguments.of("bad ", getEnvVars(new String[] {"VAR"}, new String[] {"test"}), false));
    }

    @ParameterizedTest
    @MethodSource("isExcludedTestCases")
    void testIsExcluded(String excludedValues, EnvVars envVars, boolean isExcluded) {
        assertEquals(isExcluded, new CachingConfiguration(0, excludedValues).isExcluded(envVars));
    }
}
