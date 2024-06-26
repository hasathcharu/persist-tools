-- Copyright (c) 2024 WSO2 LLC. (http://www.wso2.com).
--
-- WSO2 LLC. licenses this file to you under the Apache License,
-- Version 2.0 (the "License"); you may not use this file except
-- in compliance with the License.
-- You may obtain a copy of the License at
--
-- http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing,
-- software distributed under the License is distributed on an
-- "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
-- KIND, either express or implied.  See the License for the
-- specific language governing permissions and limitations
-- under the License.

CREATE TABLE "User" (
    "id" INT,
    "name" VARCHAR(191) NOT NULL,
    "gender" VARCHAR(6) CHECK ("gender" IN ('MALE', 'FEMALE')) NOT NULL,
    "salary" DECIMAL(65, 30),
    "drives_car" INT,
    PRIMARY KEY (id)
);

CREATE TABLE "Car" (
    "id" INT,
    "name" VARCHAR(191) NOT NULL,
    "model" VARCHAR(191) NOT NULL,
    "ownerId" INT NOT NULL,
    FOREIGN KEY ("ownerId") REFERENCES "User"("id"),
    PRIMARY KEY ("id")
);

ALTER TABLE "User" ADD FOREIGN KEY ("drives_car") REFERENCES "Car"("id");
