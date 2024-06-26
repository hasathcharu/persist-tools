import ballerina/persist as _;
import ballerina/time;
import ballerinax/persist.sql;

public enum PatientGender {
    MALE = "MALE",
    FEMALE = "FEMALE"
}

public type Appointment record {|
    readonly int id;
    int patientId;
    int doctorId;
    time:Date date;
    @sql:Relation {keys: ["patientId"]}
    Patient patient;
    @sql:Relation {keys: ["doctorId"]}
    Doctor doctor;
|};

public type Patient record {|
    readonly int id;
    string name;
    PatientGender gender;
    string nic;
    Appointment[] appointments;
|};

public type Doctor record {|
    readonly int id;
    string name;
    string specialty;
    Appointment[] appointments;
|};

