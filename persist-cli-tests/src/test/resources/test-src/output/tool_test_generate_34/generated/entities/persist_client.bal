// AUTO-GENERATED FILE. DO NOT MODIFY.

// This file is an auto-generated file by Ballerina persistence layer for model.
// It should not be modified by hand.

import ballerina/persist;
import ballerina/jballerina.java;
import ballerinax/mysql;
import ballerinax/mysql.driver as _;

const PROFILE = "profiles";
const USER = "users";

public isolated client class Client {
    *persist:AbstractPersistClient;

    private final mysql:Client dbClient;

    private final map<persist:SQLClient> persistClients;

    private final record {|persist:SQLMetadata...;|} & readonly metadata = {
        [PROFILE] : {
            entityName: "Profile",
            tableName: "Profile",
            fieldMetadata: {
                id: {columnName: "id"},
                name: {columnName: "name"},
                gender: {columnName: "gender"},
                ownerId: {columnName: "ownerId"},
                "owner.id": {relation: {entityName: "owner", refField: "id"}}
            },
            keyFields: ["id"],
            joinMetadata: {owner: {entity: User, fieldName: "owner", refTable: "User", refColumns: ["id"], joinColumns: ["ownerId"], 'type: persist:ONE_TO_ONE}}
        },
        [USER] : {
            entityName: "User",
            tableName: "User",
            fieldMetadata: {
                id: {columnName: "id"},
                "profile.id": {relation: {entityName: "profile", refField: "id"}},
                "profile.name": {relation: {entityName: "profile", refField: "name"}},
                "profile.gender": {relation: {entityName: "profile", refField: "gender"}},
                "profile.ownerId": {relation: {entityName: "profile", refField: "ownerId"}}
            },
            keyFields: ["id"],
            joinMetadata: {profile: {entity: Profile, fieldName: "profile", refTable: "Profile", refColumns: ["ownerId"], joinColumns: ["id"], 'type: persist:ONE_TO_ONE}}
        }
    };

    public isolated function init() returns persist:Error? {
        mysql:Client|error dbClient = new (host = host, user = user, password = password, database = database, port = port, options = connectionOptions);
        if dbClient is error {
            return <persist:Error>error(dbClient.message());
        }
        self.dbClient = dbClient;
        self.persistClients = {
            [PROFILE] : check new (dbClient, self.metadata.get(PROFILE)),
            [USER] : check new (dbClient, self.metadata.get(USER))
        };
    }

    isolated resource function get profiles(ProfileTargetType targetType = <>) returns stream<targetType, persist:Error?> = @java:Method {
        'class: "io.ballerina.stdlib.persist.datastore.MySQLProcessor",
        name: "query"
    } external;

    isolated resource function get profiles/[int id](ProfileTargetType targetType = <>) returns targetType|persist:Error = @java:Method {
        'class: "io.ballerina.stdlib.persist.datastore.MySQLProcessor",
        name: "queryOne"
    } external;

    isolated resource function post profiles(ProfileInsert[] data) returns int[]|persist:Error {
        persist:SQLClient sqlClient;
        lock {
            sqlClient = self.persistClients.get(PROFILE);
        }
        _ = check sqlClient.runBatchInsertQuery(data);
        return from ProfileInsert inserted in data
            select inserted.id;
    }

    isolated resource function put profiles/[int id](ProfileUpdate value) returns Profile|persist:Error {
        persist:SQLClient sqlClient;
        lock {
            sqlClient = self.persistClients.get(PROFILE);
        }
        _ = check sqlClient.runUpdateQuery(id, value);
        return self->/profiles/[id].get();
    }

    isolated resource function delete profiles/[int id]() returns Profile|persist:Error {
        Profile result = check self->/profiles/[id].get();
        persist:SQLClient sqlClient;
        lock {
            sqlClient = self.persistClients.get(PROFILE);
        }
        _ = check sqlClient.runDeleteQuery(id);
        return result;
    }

    isolated resource function get users(UserTargetType targetType = <>) returns stream<targetType, persist:Error?> = @java:Method {
        'class: "io.ballerina.stdlib.persist.datastore.MySQLProcessor",
        name: "query"
    } external;

    isolated resource function get users/[int id](UserTargetType targetType = <>) returns targetType|persist:Error = @java:Method {
        'class: "io.ballerina.stdlib.persist.datastore.MySQLProcessor",
        name: "queryOne"
    } external;

    isolated resource function post users(UserInsert[] data) returns int[]|persist:Error {
        persist:SQLClient sqlClient;
        lock {
            sqlClient = self.persistClients.get(USER);
        }
        _ = check sqlClient.runBatchInsertQuery(data);
        return from UserInsert inserted in data
            select inserted.id;
    }

    isolated resource function put users/[int id](UserUpdate value) returns User|persist:Error {
        persist:SQLClient sqlClient;
        lock {
            sqlClient = self.persistClients.get(USER);
        }
        _ = check sqlClient.runUpdateQuery(id, value);
        return self->/users/[id].get();
    }

    isolated resource function delete users/[int id]() returns User|persist:Error {
        User result = check self->/users/[id].get();
        persist:SQLClient sqlClient;
        lock {
            sqlClient = self.persistClients.get(USER);
        }
        _ = check sqlClient.runDeleteQuery(id);
        return result;
    }

    public isolated function close() returns persist:Error? {
        error? result = self.dbClient.close();
        if result is error {
            return <persist:Error>error(result.message());
        }
        return result;
    }
}

