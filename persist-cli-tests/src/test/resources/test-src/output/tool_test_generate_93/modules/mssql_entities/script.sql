-- AUTO-GENERATED FILE.

-- This file is an auto-generated file by Ballerina persistence layer for model.
-- Please verify the generated scripts and execute them against the target DB server.

DROP TABLE IF EXISTS [Car];
DROP TABLE IF EXISTS [User];

CREATE TABLE [User] (
	[id] INT NOT NULL,
	[name] VARCHAR(191) NOT NULL,
	[gender] VARCHAR(6) CHECK ([gender] IN ('MALE', 'FEMALE')) NOT NULL,
	[nic] VARCHAR(191) NOT NULL,
	[salary] DECIMAL(38,30),
	PRIMARY KEY([id])
);

CREATE TABLE [Car] (
	[id] INT NOT NULL,
	[name] VARCHAR(191) NOT NULL,
	[model] VARCHAR(191) NOT NULL,
	[ownerId] INT NOT NULL,
	FOREIGN KEY([ownerId]) REFERENCES [User]([id]),
	[driverId] INT UNIQUE NOT NULL,
	FOREIGN KEY([driverId]) REFERENCES [User]([id]),
	PRIMARY KEY([id])
);


CREATE INDEX [ownerId] ON [Car] ([ownerId]);
