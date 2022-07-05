/*
    Copyright 2022 Hendrik Iben

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.package com.github.hiben;
*/
package de.zvxeb.syshmavi;

import de.zvxeb.jres.SSLogic;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class Configuration {

    public static final String DATA_PATH_PROPERTY = "dataPath";
    public static final String DATA_PATH_ENVIRONMENT = "SS1_DATA_PATH";

    public static final String CONFIG_PATH_PROPERTY = "configPath";
    public static final String CONFIG_PATH_ENVIRONMENT = "CONFIG_PATH";

    public static final String MAP_PROPERTY = "map";
    public static final int MAP_DEFAULT = -1;

    public static final String SAVE_VIS_PROPERTY = "saveVisInfo";
    public static final boolean SAVE_VIS_DEFAULT = false;

    public static final String LOAD_VIS_PROPERTY = "loadVisInfo";
    public static final boolean LOAD_VIS_DEFAULT = true;

    private static Predicate<String> exceptionIsFalse(Predicate<String> p) {
        return s -> {
            try {
                return p.test(s);
            } catch(Exception e) {
                return false;
            }
        };
    };

    private static final Predicate<String> NOT_NULL = s -> s != null;
    private static final Predicate<String> NOT_BLANK = s -> s != null && !s.isBlank();
    private static final Predicate<String> IS_BOOLEAN = s -> s != null && ( s.equalsIgnoreCase("true") || s.equalsIgnoreCase("false") );

    private static final Function<String, String> STRING_VALUE = s -> s;
    private static final Function<String, String> TRIMMED_STRING_VALUE = s -> s.trim();

    public static final Entry<String> CONFIG_PATH =
        new Entry.Builder<String>()
            .propertyName(CONFIG_PATH_PROPERTY)
            .environmentVariable(CONFIG_PATH_ENVIRONMENT)
            .validityCheck(NOT_BLANK)
            .valueMapper(TRIMMED_STRING_VALUE)
            .defaultValue(() -> {
                String userHome = System.getProperty("user.home");
                if (userHome == null || userHome.isBlank()) {
                    userHome = System.getenv("HOME");
                }
                if (userHome == null || userHome.isBlank()) {
                    System.err.println("No HOME found!");
                    userHome = ".";
                }
                return Path.of(userHome).resolve(".syshmavi").toString();
            })
            .build()
        ;

    public static final Entry<String> DATA_PATH =
        new Entry.Builder<String>()
            .propertyName(DATA_PATH_PROPERTY)
            .environmentVariable(DATA_PATH_ENVIRONMENT)
            .validityCheck(NOT_BLANK)
            .valueMapper(TRIMMED_STRING_VALUE)
            .build()
        ;

    public static final Entry<Integer> MAP =
        new Entry.Builder<Integer>()
            .propertyName(MAP_PROPERTY)
            .defaultValue(() -> MAP_DEFAULT)
            .validityCheck(
                exceptionIsFalse(
                    s -> {
                        int map = Integer.parseInt(s);
                        if(map == -1) return true;
                        if(map < 0 || map >= SSLogic.levelNames.length) {
                            return false;
                        }
                        return true;
                    }
                )
            )
            .valueMapper(Integer::parseInt)
            .build()
        ;

    public static final Entry<Boolean> SAVE_VIS =
        new Entry.Builder<Boolean>()
            .propertyName(SAVE_VIS_PROPERTY)
            .defaultValue(() -> SAVE_VIS_DEFAULT)
            .validityCheck(IS_BOOLEAN)
            .valueMapper(Boolean::parseBoolean)
            .build();

    public static final Entry<Boolean> LOAD_VIS =
        new Entry.Builder<Boolean>()
            .propertyName(LOAD_VIS_PROPERTY)
            .defaultValue(() -> LOAD_VIS_DEFAULT)
            .validityCheck(IS_BOOLEAN)
            .valueMapper(Boolean::parseBoolean)
            .build();

    private Properties configurationProperties = new Properties();

    private String getValueFromPropertyOrEnvironmentChecked(String property, String environment, Predicate<String> check) {
        if(property!=null) {
            String value = System.getProperty(property);
            if (check.test(value)) {
                return value;
            }
        }
        if(environment!=null) {
            String value = System.getenv(environment);
            if (check.test(value)) {
                return value;
            }
        }
        return null;
    }

    public void reset() {
        configurationProperties.clear();
    }

    public void loadConfiguration() {
        reset();

        Path configBasePath = Path.of(getValueFor(CONFIG_PATH).get());

        try(FileInputStream fis = new FileInputStream(configBasePath.resolve("config.properties").toFile())) {
            configurationProperties.load(fis);
        } catch (FileNotFoundException e) {
            System.out.println("No configuration file found...");
        } catch (IOException e) {
            System.err.println("Could not load configuration: " + e.getMessage());
        }
    }

    public <A> Optional<A> getValueFor(Entry<A> configEntry) {
        String value = getValueFromPropertyOrEnvironmentChecked(configEntry.getProperty(), configEntry.getEnvironment(), configEntry::isValid);
        if(value == null) {
            value = configurationProperties.getProperty(configEntry.getProperty());
        }
        try {
            if (value != null && configEntry.isValid(value)) return Optional.of(configEntry.getValue(value));
        } catch(Exception e) {
            System.err.println("Invalid config... " + value);
        }
        if(configEntry.getDefaultValue() != null) {
            return Optional.of(configEntry.getDefaultValue());
        }
        return Optional.empty();
    }

    public static abstract class Entry<A> {
        protected String property;
        protected String environment = null;

        public Entry(String property) {
            this.property = property;
        }

        public Entry(String property, String environment) {
            this.property = property;
            this.environment = environment;
        }

        public String getProperty() { return property; };

        public String getEnvironment() { return environment; };

        public abstract boolean isValid(String value);

        public abstract A getValue(String value);

        public abstract A getDefaultValue();

        public static Entry<Boolean> booleanEntry(String property, String environment, boolean defaultValue) {
            return new Builder<Boolean>()
                    .propertyName(property)
                    .environmentVariable(environment)
                    .validityCheck(IS_BOOLEAN)
                    .valueMapper(Boolean::parseBoolean)
                    .defaultValue(() -> defaultValue)
                    .build()
                    ;
        }

        public static Entry<Boolean> booleanEntry(String property, boolean defaultValue) {
            return booleanEntry(property, null, defaultValue);
        }

        public static Entry<String> stringEntry(String property, String environment) {
            return new Builder<String>()
                    .propertyName(property)
                    .environmentVariable(environment)
                    .validityCheck(NOT_NULL)
                    .valueMapper(STRING_VALUE)
                    .build()
                ;
        }

        public static Entry<String> stringEntry(String property) {
            return stringEntry(property, null);
        }

        private static class Builder<A> {
            private String propertyName;
            private String environmentVariable;
            private Supplier<A> defaultValue;
            private Predicate<String> validityCheck;
            private Function<String, A> valueMapper;

            public Entry<A> build() {
                if(propertyName == null) throw new IllegalStateException("Property not set!");
                if(validityCheck == null) throw new IllegalStateException("Validity not set!");
                if(valueMapper == null) throw new IllegalStateException("Value mapper not set!");

                return new Entry<A>(propertyName, environmentVariable) {

                    @Override
                    public boolean isValid(String value) {
                        return validityCheck.test(value);
                    }

                    @Override
                    public A getValue(String value) {
                        return valueMapper.apply(value);
                    }

                    @Override
                    public A getDefaultValue() {
                        return defaultValue != null ? defaultValue.get() : null;
                    }
                };
            }

            public Builder<A> propertyName(String propertyName) {
                this.propertyName = propertyName;
                return this;
            }

            public Builder<A> environmentVariable(String e) {
                this.environmentVariable = e;
                return this;
            }

            public Builder<A> defaultValue(Supplier<A> defaultValue) {
                this.defaultValue = defaultValue;
                return this;
            }

            public Builder<A> validityCheck(Predicate<String> validityCheck) {
                this.validityCheck = validityCheck;
                return this;
            }

            public Builder<A> valueMapper(Function<String, A> valueMapper) {
                this.valueMapper = valueMapper;
                return this;
            }
        }
    }
}
