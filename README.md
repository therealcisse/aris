Here’s the improved version of the cqrs-core documentation, incorporating the new concept of the Provider and the extended functions for the EventHandler:

1. # Introduction

## Core Idea

cqrs-core manages the lifecycle of entity data using the CQRS (Command Query Responsibility Segregation) concept. It offers essential features for tracking events and managing checkpointing to enhance performance by minimizing the number of event queries needed.

Checkpointing occurs after a predefined number of events have been processed for a specific entity. However, the actual entity and its data are handled by end-users. The library supports various entity types using inheritance and a discriminator column, which allows storing multiple entities’ events in a single database.

2. # Key Concepts

	1.	Change (Event):

Represents an event stored in the events table, with the following attributes:
	•	Version (ULID string): Used to order events.
	•	Payload (Binary): Serialized event data using zio-schema. These events are part of sealed trait event types and are transformed into entities using EventHandlers.

	2.	Command:

Commands trigger actions within the system and are converted into events using CmdHandlers. Typically, there’s a one-to-one correspondence between commands and events.

	3.	EventHandler:

Responsible for transforming events into entities. There are two main scenarios:
	•	If an entity already exists, the event alters the entity’s state.
	•	If no entity exists, the first event creates an initial entity.
Additional functions for applying multiple events:
	•	def applyEvents(events: NonEmptyList[Change[Event]]): T: This function transforms a list of events into an entity.
	•	def applyEvents(zero: T, events: NonEmptyList[Change[Event]]): T: This function applies the list of events starting from a provided initial state (zero).

	4.	SnapshotStore:

Stores a snapshot of an entity’s state to optimize future event retrievals. However, it does not store entity data, which is managed by the Checkpointer.

	5.	EventStore:

Handles event storage and retrieval from the database. Events can be loaded either fully or from a specific snapshot version.

	6.	Checkpointer:

Manages saving the entity’s state and updating the snapshot version via the SnapshotStore.

	7.	CQRSPersistence (PostgreSQL Implementation):

Implements the persistence interface using PostgreSQL as the underlying storage system. It handles atomic transactions, event persistence, and snapshot management.

	8.	Provider:

Responsible for loading entity data. This component is implemented by end-users and provides an initial entity state before event replay.

	9.	Key:

Represents the entity’s primary key, which must be a string.

3. # PostgreSQL Persistence Implementation

The library uses a PostgresCQRSPersistence service to persist events and snapshots using PostgreSQL. This implementation includes:

	Events Table:

	•	Stores serialized event data using zio-schema.
	•	Supports multiple entity types using a discriminator column.

	Snapshots Table:

	•	Tracks the snapshot versions for entities.

PostgreSQL Schema Setup:

```sql
-- Create tables for events and snapshots
CREATE TABLE IF NOT EXISTS snapshots (
  aggregate_id TEXT NOT NULL,
  version INT NOT NULL,
  PRIMARY KEY (aggregate_id, version)
);

CREATE TABLE IF NOT EXISTS events (
  version TEXT NOT NULL PRIMARY KEY,
  aggregate_id TEXT NOT NULL,
  discriminator TEXT NOT NULL,
  payload BYTEA NOT NULL
);

CREATE INDEX idx_events_discriminator ON events (discriminator);
CREATE INDEX idx_events_aggregate_id ON events (aggregate_id);
```


Core Components:

	•	PostgresCQRSPersistence: Implements event and snapshot storage with atomic transaction support.
	•	Queries: SQL queries handle reading and writing events and snapshots.

4. # Example Tutorial (Ingestion System)

This example demonstrates a simple ingestion system implemented using the cqrs-core library and PostgreSQL persistence.

Step 1: Define the Domain - Entity, Events, and Commands

	•	Entity (Ingestion): Represents an ingestion process.

	•	Events:

      •	IngestionCreated
      •	ItemAdded
      •	ItemRemoved
      •	Commands:
      •	CreateIngestion
      •	AddItem
      •	RemoveItem

```scala
sealed trait IngestionCommand
case class CreateIngestion(id: String) extends IngestionCommand
case class AddItem(item: String) extends IngestionCommand
case class RemoveItem(item: String) extends IngestionCommand

sealed trait IngestionEvent
case class IngestionCreated(id: String) extends IngestionEvent
case class ItemAdded(item: String) extends IngestionEvent
case class ItemRemoved(item: String) extends IngestionEvent
```

Step 2: Implement Event Handling

Define an event handler that manages the state of the Ingestion entity:

```scala
object IngestionEventHandler extends EventHandler[IngestionEvent, Ingestion] {

  def applyEvents(events: NonEmptyList[Change[IngestionEvent]]): Ingestion =
    ???

  def applyEvents(zero: Ingestion, events: NonEmptyList[Change[IngestionEvent]]): Ingestion =
    ???


}
```

Step 3: Implement CQRS with PostgreSQL

Use PostgresCQRSPersistence, IngestionEventStore, and IngestionService to manage the CQRS flow.

```scala
val cqrsLayer = ZLayer.make[IngestionCQRS](
  IngestionEventStore.live(),
  SnapshotStore.live(),
  PostgresCQRSPersistence.live()
)
```

Step 4: Example Usage

Here’s how to create an ingestion entity and add items using commands:

```scala
val ingestionService = ZIO.service[IngestionCQRS]

val result = for {
  _ <- ingestionService.add("ingestion-id", CreateIngestion("ingestion-id"))
  _ <- ingestionService.add("ingestion-id", AddItem("item1"))
  _ <- ingestionService.add("ingestion-id", AddItem("item2"))
  ingestion <- ingestionService.load("ingestion-id")
} yield ingestion

result.provideLayer(cqrsLayer)
```

5. # Conclusion

This example illustrates how the cqrs-core library can be used to implement an ingestion system with PostgreSQL persistence. The core concepts of CQRS, including events, commands, checkpointing, and the addition of a Provider for loading entity data, are seamlessly integrated with database-backed storage. The system can easily be extended with more entity types, events, or persistence mechanisms.

