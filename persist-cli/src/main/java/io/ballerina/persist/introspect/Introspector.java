/*
 * Copyright (c) 2024 WSO2 LLC. (http://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.ballerina.persist.introspect;

import io.ballerina.persist.BalException;
import io.ballerina.persist.PersistToolsConstants;
import io.ballerina.persist.configuration.PersistConfiguration;
import io.ballerina.persist.inflector.CaseConverter;
import io.ballerina.persist.inflector.Pluralizer;
import io.ballerina.persist.introspectiondto.SqlColumn;
import io.ballerina.persist.introspectiondto.SqlEnum;
import io.ballerina.persist.introspectiondto.SqlForeignKey;
import io.ballerina.persist.introspectiondto.SqlTable;
import io.ballerina.persist.models.Entity;
import io.ballerina.persist.models.EntityField;
import io.ballerina.persist.models.Enum;
import io.ballerina.persist.models.EnumMember;
import io.ballerina.persist.models.Index;
import io.ballerina.persist.models.Module;
import io.ballerina.persist.models.Relation;
import io.ballerina.persist.models.SqlType;
import io.ballerina.persist.nodegenerator.DriverResolver;
import io.ballerina.persist.utils.DatabaseConnector;
import io.ballerina.persist.utils.JdbcDriverLoader;
import io.ballerina.persist.utils.ScriptRunner;
import io.ballerina.projects.Project;

import java.io.PrintStream;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static java.lang.Integer.parseInt;
import static java.lang.Integer.parseUnsignedInt;

/**
 * Database Introspector class.
 *
 *
 */
public abstract class Introspector {

    protected DatabaseConnector databaseConnector;
    protected PersistConfiguration persistConfigurations;
    protected abstract String getTablesQuery();
    protected abstract String getColumnsQuery(String tableName);
    protected abstract String getIndexesQuery(String tableName);
    protected abstract String getForeignKeysQuery(String tableName);
    protected abstract String getEnumsQuery();
    protected abstract String getBalType(SqlType sqlType);
    protected abstract boolean isEnumType(SqlColumn column);
    protected abstract List<String> extractEnumValues(String enumString);
    private List<SqlTable> tables;
    private List<SqlEnum> sqlEnums;
    private final Module.Builder moduleBuilder;
    private final Map<String, Entity> entityMap;
    private final List<SqlForeignKey> sqlForeignKeys;
    protected PrintStream errStream = System.err;

    protected Introspector() {
        this.tables = new ArrayList<>();
        this.entityMap = new HashMap<>();
        this.sqlForeignKeys = new ArrayList<>();
        this.sqlEnums = new ArrayList<>();
        this.moduleBuilder = Module.newBuilder("db");
    }

    public Module introspectDatabase(PersistConfiguration persistConfiguration) throws BalException {
        this.persistConfigurations = persistConfiguration;
        DriverResolver driverResolver = new DriverResolver(this.persistConfigurations.getSourcePath(),
                this.persistConfigurations.getProvider());
        try {
            Project driverProject = driverResolver.resolveDriverDependencies();
            try (Connection connection = prepareDatabaseConnection(driverProject)) {
                readDatabaseSchema(connection);
            } catch (SQLException e) {
                throw new BalException("failed to read database schema: " + e.getMessage());
            }
            mapDatabaseSchemaToModule();
            return moduleBuilder.build();
        } finally {
            driverResolver.deleteDriverFile();
        }
    }

    private Connection prepareDatabaseConnection(Project driverProject) throws BalException {
        JdbcDriverLoader driverLoader;
        driverLoader = databaseConnector.getJdbcDriverLoader(driverProject);
        Driver driver = databaseConnector.getJdbcDriver(driverLoader);
        try {
            return databaseConnector.getConnection(driver, persistConfigurations, true);
        } catch (SQLException e) {
            throw new BalException("failed to connect to the database: " + e.getMessage());
        }
    }

    public void readDatabaseSchema(Connection connection) throws SQLException {
        ScriptRunner sr = new ScriptRunner(connection);
        this.tables = sr.getSQLTables(this.getTablesQuery());
        this.sqlEnums = sr.getSQLEnums(this.getEnumsQuery());
        for (SqlTable table : tables) {
            sr.readColumnsOfSQLTable(table, this.getColumnsQuery(table.getTableName()));
            this.sqlForeignKeys.addAll(sr.readForeignKeysOfSQLTable
                    (table, this.getForeignKeysQuery(table.getTableName())));
            sr.readIndexesOfSQLTable(table, this.getIndexesQuery(table.getTableName()));
        }
    }

    public void mapDatabaseSchemaToModule() throws BalException {
        mapEnums();
        mapEntities();
        entityMap.forEach(moduleBuilder::addEntity);
    }

    private void mapEnums() {
        this.sqlEnums.forEach(sqlEnum -> {
            String enumName = createEnumName(sqlEnum.getEnumTableName(), sqlEnum.getEnumColumnName());
            Enum.Builder enumBuilder = Enum.newBuilder(enumName);
            extractEnumValues(sqlEnum.getFullEnumText())
                    .forEach(enumValue ->
                    enumBuilder.addMember(new EnumMember(CaseConverter.toUpperSnakeCase(enumValue), enumValue)));
            moduleBuilder.addEnum(enumName, enumBuilder.build());
        });
    }

    private void mapEntities() throws BalException {
        Map<String, Entity.Builder> entityBuilderMap = new HashMap<>();
        tables.forEach(table -> {
            String entityName = CaseConverter.toSingularPascalCase(table.getTableName());

            Entity.Builder entityBuilder = Entity.newBuilder(entityName);
            entityBuilder.setTableName(table.getTableName());
            List<EntityField> keys = new ArrayList<>();
            List<EntityField> fields = new ArrayList<>();
            table.getColumns().forEach(column -> {
                EntityField.Builder fieldBuilder = EntityField.newBuilder(
                        CaseConverter.toCamelCase(column.getColumnName()));

                fieldBuilder.setFieldColumnName(column.getColumnName());
                fieldBuilder.setArrayType(false);
                if (isEnumType(column)) {
                    fieldBuilder.setType(createEnumName(table.getTableName(), column.getColumnName()));
                } else {
                    String maxLen = column.getCharacterMaximumLength();
                    SqlType sqlType = new SqlType(
                            column.getDataType().toUpperCase(Locale.ENGLISH),
                            column.getFullDataType(),
                            column.getColumnDefault(),
                            column.getNumericPrecision() != null ? parseInt(column.getNumericPrecision()) : 0,
                            column.getNumericScale() != null ? parseInt(column.getNumericScale()) : 0,
                            (maxLen != null) ? parseUnsignedInt(maxLen) : 0,
                            persistConfigurations.getProvider()
                    );
                    String balType = this.getBalType(sqlType);
                    fieldBuilder.setType(balType);
                    fieldBuilder.setSqlType(sqlType);
                    fieldBuilder.setArrayType(sqlType.isArrayType());
                }

                fieldBuilder.setOptionalType(column.getIsNullable().equals("YES"));
                fieldBuilder.setIsDbGenerated(column.isDbGenerated());

                EntityField entityField = fieldBuilder.build();
                entityBuilder.addField(entityField);
                fields.add(entityField);
                if (column.getIsPrimaryKey()) {
                    keys.add(entityField);
                }
            });
            table.getIndexes().forEach(sqlIndex -> {
                List<EntityField> indexFields = new ArrayList<>();
                sqlIndex.getColumnNames().forEach(columnName -> fields.forEach(entityField -> {
                    if (entityField.getFieldColumnName().equals(columnName)) {
                        indexFields.add(entityField);
                    }
                }));
                Index index = new Index(sqlIndex.getIndexName(), indexFields, sqlIndex.getUnique());
                if (index.isUnique()) {
                    entityBuilder.addUniqueIndex(index);
                } else {
                    entityBuilder.addIndex(index);
                }
            });
            entityBuilder.setKeys(keys);
            entityBuilderMap.put(entityBuilder.getEntityName(), entityBuilder);
        });
        HashMap<String, Integer> ownerFieldNames = new HashMap<>();
        HashMap<String, Integer> assocFieldNames = new HashMap<>();
        for (SqlForeignKey sqlForeignKey : this.sqlForeignKeys) {
            Entity.Builder ownerEntityBuilder = entityBuilderMap
                    .get(CaseConverter.toSingularPascalCase(sqlForeignKey.getTableName()));
            Entity.Builder assocEntityBuilder = entityBuilderMap
                    .get(CaseConverter.toSingularPascalCase(sqlForeignKey.getReferencedTableName()));
            if (!new HashSet<>(sqlForeignKey.getReferencedColumnNames()).containsAll(assocEntityBuilder.getKeys()
                    .stream().map(EntityField::getFieldColumnName).toList())) {
                throw new BalException("bal persist does not support foreign key references to unique " +
                        "keys.");
            }
            boolean isReferenceMany = inferRelationshipCardinality
                    (ownerEntityBuilder.build(), sqlForeignKey) == Relation.RelationType.MANY;
            String assocFieldName = isReferenceMany ?
                    Pluralizer.pluralize(ownerEntityBuilder.getEntityName().toLowerCase(Locale.ENGLISH))
                    : ownerEntityBuilder.getEntityName().toLowerCase(Locale.ENGLISH);
            if (assocFieldNames.containsKey(assocEntityBuilder.getEntityName() + assocFieldName)) {
                assocFieldNames.put(assocEntityBuilder.getEntityName() + assocFieldName,
                        assocFieldNames.get(assocEntityBuilder.getEntityName() + assocFieldName) + 1);
                assocFieldName = assocFieldName +
                        assocFieldNames.get(assocEntityBuilder.getEntityName() + assocFieldName);
            } else {
                assocFieldNames.put(assocEntityBuilder.getEntityName() + assocFieldName, 0);
            }
            EntityField.Builder assocFieldBuilder = EntityField
                    .newBuilder(assocFieldName);
            assocFieldBuilder.setType(ownerEntityBuilder.getEntityName());

            String ownerFieldName = assocEntityBuilder.getEntityName().toLowerCase(Locale.ENGLISH);
            if (ownerFieldNames.containsKey(ownerEntityBuilder.getEntityName() + ownerFieldName)) {
                ownerFieldNames.put(ownerEntityBuilder.getEntityName() + ownerFieldName,
                        ownerFieldNames.get(ownerEntityBuilder.getEntityName() + ownerFieldName) + 1);
                ownerFieldName = ownerFieldName +
                        ownerFieldNames.get(ownerEntityBuilder.getEntityName() + ownerFieldName);
            } else {
                ownerFieldNames.put(ownerEntityBuilder.getEntityName() + ownerFieldName, 0);
            }

            EntityField.Builder ownerFieldBuilder = EntityField
                    .newBuilder(ownerFieldName);
            ownerFieldBuilder.setType(assocEntityBuilder.getEntityName());

            assocFieldBuilder.setArrayType(isReferenceMany);
            assocFieldBuilder.setOptionalType(!isReferenceMany);
            ownerFieldBuilder.setRelationRefs(sqlForeignKey.getColumnNames().stream().map(columnName ->
                    ownerEntityBuilder.build().getFieldByColumnName(columnName).getFieldName()).toList());

            EntityField ownerField = ownerFieldBuilder.build();

            assocEntityBuilder.addField(assocFieldBuilder.build());
            ownerEntityBuilder.addField(ownerField);
        }

        entityBuilderMap.forEach((key, value) -> entityMap.put(key, value.build()));
    }

    private String createEnumName(String tableName, String columnName) {
        return CaseConverter.toSingularPascalCase(tableName) + CaseConverter.toSingularPascalCase(columnName);
    }

    private Relation.RelationType inferRelationshipCardinality(Entity ownerEntity, SqlForeignKey foreignKey) {
        List<EntityField> ownerColumns = new ArrayList<>();
        foreignKey.getColumnNames().forEach(columnName ->
                ownerColumns.add(ownerEntity.getFieldByColumnName(columnName)));
        boolean isUniqueIndexPresent = ownerEntity.getUniqueIndexes().stream()
                .anyMatch(index -> index.getFields().equals(ownerColumns));
        if (ownerEntity.getKeys().equals(ownerColumns)) {
            return Relation.RelationType.ONE;
        } else if (isUniqueIndexPresent) {
            return Relation.RelationType.ONE;
        } else {
            return Relation.RelationType.MANY;
        }
    }

    protected String getBalTypeForCommonDataTypes(SqlType sqlType) {
        return switch (sqlType.getTypeName()) {
            case PersistToolsConstants.SqlTypes.INT,
                    PersistToolsConstants.SqlTypes.INTEGER,
                    PersistToolsConstants.SqlTypes.TINYINT,
                    PersistToolsConstants.SqlTypes.SMALLINT,
                    PersistToolsConstants.SqlTypes.MEDIUMINT,
                    PersistToolsConstants.SqlTypes.BIGINT,
                    PersistToolsConstants.SqlTypes.SERIAL,
                    PersistToolsConstants.SqlTypes.BIGSERIAL,
                    PersistToolsConstants.SqlTypes.INT4,
                    PersistToolsConstants.SqlTypes.INT2,
                    PersistToolsConstants.SqlTypes.INT8 ->
                    PersistToolsConstants.BallerinaTypes.INT;
            case PersistToolsConstants.SqlTypes.BOOLEAN,
                    PersistToolsConstants.SqlTypes.BOOL -> PersistToolsConstants.BallerinaTypes.BOOLEAN;
            case PersistToolsConstants.SqlTypes.DECIMAL,
                    PersistToolsConstants.SqlTypes.NUMERIC -> PersistToolsConstants.BallerinaTypes.DECIMAL;
            case PersistToolsConstants.SqlTypes.DOUBLE,
                    PersistToolsConstants.SqlTypes.FLOAT,
                    PersistToolsConstants.SqlTypes.FLOAT4,
                    PersistToolsConstants.SqlTypes.FLOAT8-> PersistToolsConstants.BallerinaTypes.FLOAT;
            case PersistToolsConstants.SqlTypes.DATE -> PersistToolsConstants.BallerinaTypes.DATE;
            case PersistToolsConstants.SqlTypes.TIME,
                    PersistToolsConstants.SqlTypes.TIMETZ -> PersistToolsConstants.BallerinaTypes.TIME_OF_DAY;
            case PersistToolsConstants.SqlTypes.TIME_STAMP,
                    PersistToolsConstants.SqlTypes.TIME_STAMPTZ -> PersistToolsConstants.BallerinaTypes.UTC;
            case PersistToolsConstants.SqlTypes.DATE_TIME2,
                    PersistToolsConstants.SqlTypes.DATE_TIME ->
                    PersistToolsConstants.BallerinaTypes.CIVIL;
            case PersistToolsConstants.SqlTypes.VARCHAR,
                    PersistToolsConstants.SqlTypes.CHAR,
                    PersistToolsConstants.SqlTypes.CHARACTER,
                    PersistToolsConstants.SqlTypes.BPCHAR,
                    PersistToolsConstants.SqlTypes.TEXT,
                    PersistToolsConstants.SqlTypes.MEDIUMTEXT,
                    PersistToolsConstants.SqlTypes.LONGTEXT,
                    PersistToolsConstants.SqlTypes.TINYTEXT ->
                    PersistToolsConstants.BallerinaTypes.STRING;
            case PersistToolsConstants.SqlTypes.LONG_BLOB,
                    PersistToolsConstants.SqlTypes.MEDIUM_BLOB,
                    PersistToolsConstants.SqlTypes.TINY_BLOB,
                    PersistToolsConstants.SqlTypes.BINARY,
                    PersistToolsConstants.SqlTypes.VARBINARY,
                    PersistToolsConstants.SqlTypes.BLOB,
                    PersistToolsConstants.SqlTypes.BYTEA->
                    PersistToolsConstants.BallerinaTypes.BYTE;
            default -> {
                errStream.println("WARNING Unsupported SQL type found: " + sqlType.getFullDataType());
                yield PersistToolsConstants.UNSUPPORTED_TYPE;
            }
        };
    }


}
