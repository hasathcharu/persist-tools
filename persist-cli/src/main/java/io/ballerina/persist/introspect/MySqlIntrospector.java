/*
 *  Copyright (c) 2024 WSO2 LLC. (http://www.wso2.com).
 *
 *  WSO2 LLC. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package io.ballerina.persist.introspect;

import io.ballerina.persist.PersistToolsConstants;
import io.ballerina.persist.introspectiondto.SqlColumn;
import io.ballerina.persist.models.SqlType;
import io.ballerina.persist.utils.DatabaseConnector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.ballerina.persist.PersistToolsConstants.MYSQL_DRIVER_CLASS;
import static io.ballerina.persist.nodegenerator.syntax.constants.BalSyntaxConstants.JDBC_URL_WITH_DATABASE_MYSQL;

public class MySqlIntrospector extends Introspector {

    public MySqlIntrospector() {
        databaseConnector = new DatabaseConnector(JDBC_URL_WITH_DATABASE_MYSQL, MYSQL_DRIVER_CLASS);
    }

    @Override
    public String getTablesQuery() {
        String formatQuery = """
            SELECT DISTINCT
                table_info.table_name AS table_name,
                table_info.create_options AS create_options,
                table_info.table_comment AS table_comment
            FROM
                information_schema.tables AS table_info
                JOIN information_schema.columns AS column_info
                ON column_info.table_name = table_info.table_name
            WHERE
                table_info.table_schema= '%s' AND
                column_info.table_schema = '%s' AND
                table_info.table_type = 'BASE TABLE'
            ORDER BY
                table_info.table_name;
            """;
        formatQuery = formatQuery.replace("\r\n", "%n");
        return String.format(formatQuery, this.persistConfigurations.getDbConfig().getDatabase(),
                this.persistConfigurations.getDbConfig().getDatabase());
    }

    @Override
    public String getColumnsQuery(String tableName) {
        String formatQuery = """
            SELECT
                column_name column_name,
                data_type data_type,
                column_type full_data_type,
                character_maximum_length character_maximum_length,
                numeric_precision numeric_precision,
                numeric_scale numeric_scale,
                datetime_precision datetime_precision,
                column_default column_default,
                is_nullable is_nullable,
                extra extra,
                table_name table_name,
                column_key column_key,
                IF(column_comment = '', NULL, column_comment) AS column_comment,
                IF(extra = 'auto_increment', 1, 0) AS dbgenerated,
                NULL AS check_constraint
            FROM
                information_schema.columns
            WHERE
                table_schema = '%s'
                AND table_name = '%s'
            ORDER BY
                ordinal_position ASC;
            """;
        formatQuery = formatQuery.replace("\r\n", "%n");
        return String.format(formatQuery, this.persistConfigurations.getDbConfig().getDatabase(), tableName);
    }

    @Override
    public String getIndexesQuery(String tableName) {
        String formatQuery = """
            SELECT
                table_name AS table_name,
                index_name AS index_name,
                column_name AS column_name,
                seq_in_index AS seq_in_index,
                collation AS column_order,
                IF(non_unique = '1', 0, 1) AS is_unique
            FROM
                information_schema.statistics
            WHERE
                table_schema = '%s'
                AND table_name = '%s'
                AND index_name != 'PRIMARY'
            ORDER BY
                BINARY index_name,
                seq_in_index;
            """;
        formatQuery = formatQuery.replace("\r\n", "%n");
        return String.format(formatQuery, this.persistConfigurations.getDbConfig().getDatabase(), tableName);
    }

    @Override
    public String getForeignKeysQuery(String tableName) {
        String formatQuery = """
                SELECT
                    kcu.constraint_name constraint_name,
                    kcu.column_name column_name,
                    kcu.referenced_table_name referenced_table_name,
                    kcu.referenced_column_name referenced_column_name,
                    kcu.ordinal_position ordinal_position,
                    kcu.table_name table_name,
                    rc.delete_rule delete_rule,
                    rc.update_rule update_rule
                FROM
                    information_schema.key_column_usage AS kcu
                    INNER JOIN information_schema.referential_constraints AS rc ON
                    BINARY kcu.constraint_name = BINARY rc.constraint_name
                WHERE
                    BINARY kcu.table_schema = '%s'
                    AND rc.constraint_schema = '%s'
                    AND kcu.table_name = '%s'
                    AND kcu.referenced_column_name IS NOT NULL
                ORDER BY
                    BINARY kcu.table_schema,
                    BINARY kcu.table_name,
                    BINARY kcu.constraint_name,
                    kcu.ordinal_position;
                """;
        formatQuery = formatQuery.replace("\r\n", "%n");
        return String.format(formatQuery, this.persistConfigurations.getDbConfig().getDatabase(),
                this.persistConfigurations.getDbConfig().getDatabase(), tableName);
    }

    @Override
    protected String getEnumsQuery() {
        String formatQuery = """
            SELECT
                column_name column_name,
                column_type full_enum_type,
                table_name table_name
            FROM
                information_schema.columns
            WHERE
                table_schema = '%s'
                AND data_type = 'enum'
            ORDER BY
                ordinal_position ASC;
            """;
        formatQuery = formatQuery.replace("\r\n", "%n");
        return String.format(formatQuery, this.persistConfigurations.getDbConfig().getDatabase());
    }

    @Override
    protected boolean isEnumType(SqlColumn column) {
        return "enum".equalsIgnoreCase(column.getDataType());
    }

    @Override
    protected List<String> extractEnumValues(String enumString) {
        List<String> enumValues = new ArrayList<>();

        // Using regex to extract values inside parentheses
        Pattern pattern = Pattern.compile("\\((.*?)\\)");
        Matcher matcher = pattern.matcher(enumString);

        if (matcher.find()) {
            // Group 1 contains the values inside parentheses
            String valuesInsideParentheses = matcher.group(1);

            // Split the values by comma
            String[] valuesArray = valuesInsideParentheses.split(",");
            Arrays.stream(valuesArray).map(value -> {
                value = value.trim();
                value = value.replace("'", "");
                return value.trim();
            }).forEach(enumValues::add);
        }

        return enumValues;
    }

    @Override
    protected String getBalType(SqlType sqlType) {
        if (Objects.equals(sqlType.getFullDataType(), PersistToolsConstants.SqlTypes.BOOLEAN_ALT)) {
            return PersistToolsConstants.BallerinaTypes.BOOLEAN;
        }
        return getBalTypeForCommonDataTypes(sqlType);
    }

}
