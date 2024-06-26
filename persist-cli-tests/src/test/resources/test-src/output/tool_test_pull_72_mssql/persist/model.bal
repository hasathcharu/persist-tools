import ballerina/persist as _;
import ballerinax/persist.sql;

public enum UserGender {
    FEMALE = "FEMALE",
    MALE = "MALE"
}

public type User record {|
    @sql:Generated
    readonly int id;
    string name;
    UserGender gender;
    string nic;
    decimal salary;
|};

